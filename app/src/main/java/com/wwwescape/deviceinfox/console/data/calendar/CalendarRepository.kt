package com.wwwescape.deviceinfox.console.data.calendar

import android.net.Uri
import com.wwwescape.deviceinfox.console.data.db.CalendarAttachmentDao
import com.wwwescape.deviceinfox.console.data.db.CalendarAttachmentEntity
import com.wwwescape.deviceinfox.console.data.db.CalendarDao
import com.wwwescape.deviceinfox.console.data.db.CalendarEventEntity
import com.wwwescape.deviceinfox.console.data.db.CalendarEventWithAttachments
import com.wwwescape.deviceinfox.console.data.db.EventType
import com.wwwescape.deviceinfox.console.data.db.isIntimacyEligible
import com.wwwescape.deviceinfox.console.data.db.referenceDateEpochMillis
import com.wwwescape.deviceinfox.console.data.db.supportsCancellation
import com.wwwescape.deviceinfox.console.data.db.supportsRecurrence
import com.wwwescape.deviceinfox.console.data.messaging.MediaStorage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.network.CalendarApi
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.IntimacyLogDto
import com.wwwescape.deviceinfox.console.data.network.dto.MediaAssetOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.MediaCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import com.wwwescape.deviceinfox.console.data.network.dto.toEventType
import com.wwwescape.deviceinfox.console.data.network.dto.toEventTypeDto
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateTimeString
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/** What [EventEditorDialog][com.wwwescape.deviceinfox.console.ui.calendar.EventEditorDialog]'s
 * Intimacy Log section collects — every field optional. Passed to [CalendarRepository.saveEvent]
 * as a single unit since,
 * like [CalendarEventEntity]'s `intimacy*` columns, there's exactly one (or none) per event. */
data class IntimacyLogInput(
    val rating: Int?,
    val durationMinutes: Int?,
    val initiatedByPartnerId: String?,
    val protectionUsed: Boolean?,
    val positions: List<String>,
    val locations: List<String>,
    val moods: List<String>,
    val rounds: Int?,
    val orgasmedByPartnerId: String?,
)

/** A just-uploaded (never yet downloaded — the sender already has the bytes) attachment, ready
 * to become a [CalendarAttachmentEntity] once the event itself has been created/updated
 * server-side and its id is known. */
private data class NewCalendarAttachment(
    val assetId: String,
    val kind: MessageAttachmentKind,
    val filePath: String,
    val mimeType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val sizeBytes: Long?,
    val durationMs: Int?,
    val originalFilename: String?,
    val localFullFilePath: String?,
)

/**
 * Real, server-backed calendar (Phase 11.6) — `Room is what the UI sees` still holds: [agenda]
 * is the same Room-backed `Flow` `CalendarViewModel` has always observed, mutations call the API
 * first and only write to Room on success. Every [EventType] shares this one table/endpoint —
 * the old ad-hoc/reminder split was merged away.
 *
 * `calendar_events` pushes over the WebSocket (`calendar_service.py` calls
 * `notification_service.notify_user` on every create/update/delete) — deltas are applied
 * directly from the event payload, same as `MessageRepository`. [refresh] (a full replace-sync)
 * still runs once eagerly on construction and again every time `CalendarViewModel` is created,
 * since WS delivery only reaches a *live* connection — anything the partner changed while this
 * device's console was closed needs the catch-up fetch. */
@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarDao,
    private val calendarAttachmentDao: CalendarAttachmentDao,
    private val calendarApi: CalendarApi,
    private val mediaStorage: MediaStorage,
    private val webSocketClient: ConsoleWebSocketClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _calendarWipedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires only when the *partner's* device wipes the calendar (Settings → Calendar → Delete
     * data, on their end) — this device's own screen already updates reactively via Room. */
    val calendarWipedEvent: SharedFlow<Unit> = _calendarWipedEvent.asSharedFlow()

    init {
        scope.launch { runCatching { refresh() } }
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleWsEvent(event) } }
        }
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "calendar.event.created", "calendar.event.updated" -> {
                val dto = event.data["event"]?.let { json.decodeFromJsonElement<CalendarEventOutDto>(it) } ?: return
                val previous = calendarDao.getById(dto.id)
                val (coverMediaId, coverFilePath) = resolveCoverPhoto(dto.coverMedia, previous)
                calendarDao.upsert(dto.toEntity(coverMediaId, coverFilePath))
                reconcileAttachments(dto.id, dto.attachments)
            }
            "calendar.event.deleted" -> {
                val id = event.data.eventId() ?: return
                sweepEventFiles(id)
                calendarDao.delete(id)
            }
            "calendar.wiped" -> {
                sweepAllFiles()
                calendarDao.deleteAll()
                _calendarWipedEvent.tryEmit(Unit)
            }
        }
    }

    private fun JsonObject.eventId(): String? = this["event_id"]?.jsonPrimitive?.content

    /** Ascending by [CalendarItem.displayAtEpochMillis]; the UI groups this by month. A
     * recurring entry's next occurrence is recomputed against "now" on every emission (rather
     * than persisting it, which would only advance when something explicitly re-saves the row)
     * so it's always correct even though there's no background job to refresh it as days pass. */
    val agenda: Flow<List<CalendarItem>> = calendarDao.observeAllWithAttachments().map { events ->
        events.map { it.toCalendarItem() }.sortedBy { it.displayAtEpochMillis }
    }

    /** Every date touched by an intimacy-logged event — the Period Tracker's own calendar
     * (`CycleCalendarView`) reads this to mark those days, entirely independent of this
     * repository's own agenda UI. A multi-day event marks every day in its span, not just its
     * start. Reactive off the same Room-backed [agenda] everything else here already uses — no
     * separate query, and an event/intimacy deletion or edit flows through automatically. */
    val intimacyDates: Flow<Set<LocalDate>> = agenda.map { items ->
        val zoneId = ZoneId.systemDefault()
        items.filter { it.hasIntimacyLogged }.flatMap { item ->
            val start = item.startAtEpochMillis.toLocalDate(zoneId)
            val end = (item.endAtEpochMillis ?: item.startAtEpochMillis).toLocalDate(zoneId)
            // Capped defensively — same reasoning as CalendarScreen's buildItemsByDate.
            generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.take(370)
        }.toSet()
    }

    /** Full replace-sync. Uses a generous fixed window (10 years back, 10 years forward) rather
     * than true pagination — `GET /calendar/events` requires bounded `from`/`to`, but a couple's
     * shared calendar is expected to stay small enough that this is simpler than incremental
     * sync for the same reason messaging's is capped at 5 pages. */
    suspend fun refresh() {
        val now = Calendar.getInstance()
        val from = (now.clone() as Calendar).apply { add(Calendar.YEAR, -10) }.timeInMillis.toIsoDateTimeString()
        val to = (now.clone() as Calendar).apply { add(Calendar.YEAR, 10) }.timeInMillis.toIsoDateTimeString()

        val events = consoleApiCall { calendarApi.list(from, to) }
        val entities = events.map { dto ->
            val previous = calendarDao.getById(dto.id)
            val (coverMediaId, coverFilePath) = resolveCoverPhoto(dto.coverMedia, previous)
            dto.toEntity(coverMediaId, coverFilePath)
        }
        calendarDao.replaceAll(entities)
        events.forEach { reconcileAttachments(it.id, it.attachments) }
    }

    /** Downloads (or reuses/sweeps) the event's single cover photo — same eager-download-on-change
     * treatment [reconcileAttachments] gives `CALENDAR_IMAGE` attachments, just for the one
     * distinguished "cover" asset instead of the general attachments list. Compares against
     * [previous]'s already-stored row so an unchanged cover isn't re-downloaded and a
     * removed/replaced one has its old file swept. */
    private suspend fun resolveCoverPhoto(
        remoteCover: MediaAssetOutDto?,
        previous: CalendarEventEntity?,
    ): Pair<String?, String?> {
        if (remoteCover == null) {
            previous?.coverFilePath?.let { mediaStorage.deleteFile(it) }
            return null to null
        }
        if (remoteCover.id == previous?.coverMediaId && previous.coverFilePath != null) {
            return previous.coverMediaId to previous.coverFilePath
        }
        previous?.coverFilePath?.let { mediaStorage.deleteFile(it) }
        val downloaded = runCatching { mediaStorage.downloadToMediaDir(remoteCover.id, isVoice = false) }.getOrNull()
            ?: return null to null
        return remoteCover.id to downloaded.filePath
    }

    /** Diffs the locally-cached attachment rows for [eventId] against the server's current,
     * authoritative [remoteAssets] list (by media id) — deletes (and sweeps the files of) rows
     * for attachments no longer present, and inserts rows for newly-present ones. Mirrors
     * `MessageRepository.applyRemoteMessage`'s three per-kind branches for the "insert" side
     * (image: eager full download; video: eager thumbnail + lazy full bytes; document: metadata
     * only, fully lazy) — same [MediaStorage] functions, just keyed to an event instead of a
     * message. Called after every full sync and every WS create/update delta, so it also covers
     * "attachment added/removed by the partner's device". */
    private suspend fun reconcileAttachments(eventId: String, remoteAssets: List<MediaAssetOutDto>) {
        val existing = calendarAttachmentDao.getForEvent(eventId)
        val remoteIds = remoteAssets.map { it.id }.toSet()
        val existingMediaIds = existing.mapNotNull { it.mediaId }.toSet()

        existing.filter { it.mediaId != null && it.mediaId !in remoteIds }.forEach { row ->
            listOfNotNull(row.filePath.takeIf { it.isNotBlank() }, row.localVideoFilePath, row.localDocumentFilePath)
                .forEach { mediaStorage.deleteFile(it) }
            calendarAttachmentDao.delete(row.id)
        }

        remoteAssets.filter { it.id !in existingMediaIds }.forEach { asset ->
            when (asset.category) {
                MediaCategoryDto.CALENDAR_IMAGE -> {
                    val downloaded = runCatching { mediaStorage.downloadToMediaDir(asset.id, isVoice = false) }.getOrNull()
                    if (downloaded != null) {
                        calendarAttachmentDao.insert(
                            CalendarAttachmentEntity(
                                id = UUID.randomUUID().toString(),
                                eventId = eventId,
                                attachmentKind = "IMAGE",
                                filePath = downloaded.filePath,
                                mimeType = downloaded.mimeType,
                                widthPx = downloaded.widthPx,
                                heightPx = downloaded.heightPx,
                                sizeBytes = File(downloaded.filePath).length(),
                                mediaId = asset.id,
                            ),
                        )
                    }
                }
                MediaCategoryDto.CALENDAR_VIDEO -> {
                    val thumbnailId = asset.thumbnailMediaId
                    val downloadedThumbnail = thumbnailId?.let {
                        runCatching { mediaStorage.downloadToMediaDir(it, isVoice = false) }.getOrNull()
                    }
                    if (downloadedThumbnail != null) {
                        calendarAttachmentDao.insert(
                            CalendarAttachmentEntity(
                                id = UUID.randomUUID().toString(),
                                eventId = eventId,
                                attachmentKind = "VIDEO",
                                filePath = downloadedThumbnail.filePath,
                                mimeType = asset.mimeType,
                                widthPx = downloadedThumbnail.widthPx,
                                heightPx = downloadedThumbnail.heightPx,
                                sizeBytes = asset.sizeBytes,
                                mediaId = asset.id,
                                durationMs = asset.durationMs,
                                localVideoFilePath = null,
                            ),
                        )
                    }
                }
                MediaCategoryDto.CALENDAR_DOCUMENT -> {
                    calendarAttachmentDao.insert(
                        CalendarAttachmentEntity(
                            id = UUID.randomUUID().toString(),
                            eventId = eventId,
                            attachmentKind = "DOCUMENT",
                            filePath = "",
                            mimeType = asset.mimeType,
                            widthPx = null,
                            heightPx = null,
                            sizeBytes = asset.sizeBytes,
                            mediaId = asset.id,
                            originalFilename = asset.originalFilename,
                            localDocumentFilePath = null,
                        ),
                    )
                }
                else -> Unit
            }
        }
    }

    private suspend fun sweepEventFiles(eventId: String) {
        calendarDao.getById(eventId)?.coverFilePath?.let { mediaStorage.deleteFile(it) }
        val rows = calendarAttachmentDao.getForEvent(eventId)
        rows.forEach { row ->
            listOfNotNull(row.filePath.takeIf { it.isNotBlank() }, row.localVideoFilePath, row.localDocumentFilePath)
                .forEach { mediaStorage.deleteFile(it) }
        }
    }

    private suspend fun sweepAllFiles() {
        calendarDao.getAllSnapshot().forEach { event ->
            event.coverFilePath?.let { mediaStorage.deleteFile(it) }
        }
        val rows = calendarAttachmentDao.getAllSnapshot()
        rows.forEach { row ->
            listOfNotNull(row.filePath.takeIf { it.isNotBlank() }, row.localVideoFilePath, row.localDocumentFilePath)
                .forEach { mediaStorage.deleteFile(it) }
        }
    }

    /** [newMediaUris] come from the editor's multi-select gallery picker (images and/or videos,
     * kind resolved per-uri the same way `ComposerBar`'s picker already does) and [newDocumentUris]
     * from its multi-select document picker — always uploaded fresh. [keptAttachmentMediaIds] are
     * pre-existing attachments (edit mode only) the user didn't remove in the editor. Every new
     * file is uploaded *before* the event API call, since its resulting media id has to be part
     * of the same create/update payload (mirrors `sendImage`/`sendVideo`/`sendDocument`'s
     * per-kind copy-then-upload sequence, called once per file — same "call single-file upload
     * plumbing N times" shape `VaultRepository.importImages` already establishes). */
    suspend fun saveEvent(
        id: String?,
        title: String,
        type: EventType,
        notes: String?,
        startAtEpochMillis: Long,
        endAtEpochMillis: Long?,
        isAllDay: Boolean,
        location: String?,
        recurrenceRule: String?,
        cancelled: Boolean,
        cancellationReason: String?,
        cancelledBy: String?,
        reminderMinutesBefore: List<Int>,
        intimacy: IntimacyLogInput?,
        newMediaUris: List<Uri> = emptyList(),
        newDocumentUris: List<Uri> = emptyList(),
        keptAttachmentMediaIds: List<String> = emptyList(),
        /** A freshly-picked cover photo, uploaded here same as [newMediaUris]. Null means
         * "unchanged" — the editor's own local state already carries forward the existing cover
         * (via [keptCoverMediaId]) when the user doesn't tap the hero banner to replace it; there's
         * no separate "remove cover" affordance today. */
        newCoverPhotoUri: Uri? = null,
        keptCoverMediaId: String? = null,
    ) {
        val startAt = startAtEpochMillis.toIsoDateTimeString()
        val endAt = endAtEpochMillis?.toIsoDateTimeString()
        val typeDto = type.toEventTypeDto()
        // Authoritative clamp: only recurring-capable types may ever reach the server with a
        // recurrence_rule, regardless of what the UI passed in — see EventType.supportsRecurrence.
        val recurrence = recurrenceRule.takeIf { type.supportsRecurrence }
        // Same clamp for Cancelled — only the cancellable types may ever reach the server with
        // it set, regardless of what the UI passed in — see EventType.supportsCancellation.
        val cancelledClamped = cancelled && type.supportsCancellation
        // The reason only ever travels alongside a cancellation actually taking effect — mirrors
        // the server's own "reason implies cancelled=true" rule in calendar_service.
        val cancellationReasonClamped = cancellationReason.takeIf { cancelledClamped }
        // Same clamp for "who cancelled" — never travels without an active cancellation either.
        val cancelledByClamped = cancelledBy.takeIf { cancelledClamped }
        // Same clamp for the Intimacy Log — only the 5 eligible types, and only once its own
        // reference date is today-or-past, may ever reach the server with one attached.
        val eligible = isIntimacyEligible(type, referenceDateEpochMillis(startAtEpochMillis, endAtEpochMillis))
        val intimacyDto = intimacy?.takeIf { eligible }?.let {
            IntimacyLogDto(
                it.rating, it.durationMinutes, it.initiatedByPartnerId, it.protectionUsed, it.positions, it.locations,
                it.moods, it.rounds, it.orgasmedByPartnerId,
            )
        }

        val newAttachments = newMediaUris.map { uploadNewMediaAttachment(it) } + newDocumentUris.map { uploadNewDocumentAttachment(it) }
        val attachmentMediaIds = keptAttachmentMediaIds + newAttachments.map { it.assetId }
        val coverMediaId = newCoverPhotoUri?.let { uploadNewCoverPhoto(it) } ?: keptCoverMediaId

        val result = if (id == null) {
            consoleApiCall {
                calendarApi.create(
                    CalendarEventCreateDto(
                        typeDto, title, notes, startAt, endAt, isAllDay, location, recurrence,
                        cancelledClamped, cancellationReasonClamped, cancelledByClamped, reminderMinutesBefore, intimacyDto,
                        attachmentMediaIds, coverMediaId,
                    ),
                )
            }
        } else {
            consoleApiCall {
                calendarApi.update(
                    id,
                    CalendarEventUpdateDto(
                        typeDto, title, notes, startAt, endAt, isAllDay, location, recurrence,
                        cancelledClamped, cancellationReasonClamped, cancelledByClamped, reminderMinutesBefore, intimacyDto,
                        attachmentMediaIds, coverMediaId,
                    ),
                )
            }
        }
        val previous = if (id != null) calendarDao.getById(id) else null
        val (resolvedCoverMediaId, resolvedCoverFilePath) = resolveCoverPhoto(result.coverMedia, previous)
        calendarDao.upsert(result.toEntity(resolvedCoverMediaId, resolvedCoverFilePath))

        // Local reconciliation: drop any existing row the user removed (edit mode), leave kept
        // rows untouched, insert one new row per just-uploaded file with its local path already
        // populated — the sender has the file, no self-download (mirrors sendVideo's
        // localVideoFilePath pattern).
        calendarAttachmentDao.getForEvent(result.id)
            .filter { it.mediaId != null && it.mediaId !in keptAttachmentMediaIds && newAttachments.none { n -> n.assetId == it.mediaId } }
            .forEach { row ->
                listOfNotNull(row.filePath.takeIf { it.isNotBlank() }, row.localVideoFilePath, row.localDocumentFilePath)
                    .forEach { mediaStorage.deleteFile(it) }
                calendarAttachmentDao.delete(row.id)
            }
        newAttachments.forEach { attachment ->
            calendarAttachmentDao.insert(
                CalendarAttachmentEntity(
                    id = UUID.randomUUID().toString(),
                    eventId = result.id,
                    attachmentKind = attachment.kind.name,
                    filePath = attachment.filePath,
                    mimeType = attachment.mimeType,
                    widthPx = attachment.widthPx,
                    heightPx = attachment.heightPx,
                    sizeBytes = attachment.sizeBytes,
                    mediaId = attachment.assetId,
                    durationMs = attachment.durationMs,
                    localVideoFilePath = attachment.localFullFilePath.takeIf { attachment.kind == MessageAttachmentKind.VIDEO },
                    originalFilename = attachment.originalFilename,
                    localDocumentFilePath = attachment.localFullFilePath.takeIf { attachment.kind == MessageAttachmentKind.DOCUMENT },
                ),
            )
        }
    }

    private suspend fun uploadNewMediaAttachment(uri: Uri): NewCalendarAttachment {
        val mimeType = mediaStorage.resolveMimeType(uri)
        return if (mimeType?.startsWith("video/") == true) {
            val copied = mediaStorage.copyVideoToPrivateStorage(uri)
            val probe = mediaStorage.probeVideoDurationAndThumbnail(copied.filePath)
            val thumbnailAsset = probe.thumbnailFilePath?.let { thumbPath ->
                mediaStorage.uploadToServer(thumbPath, "image/jpeg", MediaCategoryDto.CALENDAR_IMAGE)
            }
            val videoAsset = mediaStorage.uploadToServer(
                copied.filePath,
                copied.mimeType,
                MediaCategoryDto.CALENDAR_VIDEO,
                durationMs = probe.durationMs,
                thumbnailMediaId = thumbnailAsset?.id,
            )
            NewCalendarAttachment(
                assetId = videoAsset.id,
                kind = MessageAttachmentKind.VIDEO,
                filePath = probe.thumbnailFilePath ?: copied.filePath,
                mimeType = copied.mimeType,
                widthPx = probe.thumbnailWidthPx,
                heightPx = probe.thumbnailHeightPx,
                sizeBytes = copied.sizeBytes,
                durationMs = probe.durationMs,
                originalFilename = null,
                localFullFilePath = copied.filePath,
            )
        } else {
            val copied = mediaStorage.copyImageToPrivateStorage(uri)
            val asset = mediaStorage.uploadToServer(copied.filePath, copied.mimeType, MediaCategoryDto.CALENDAR_IMAGE)
            NewCalendarAttachment(
                assetId = asset.id,
                kind = MessageAttachmentKind.IMAGE,
                filePath = copied.filePath,
                mimeType = copied.mimeType,
                widthPx = copied.widthPx,
                heightPx = copied.heightPx,
                sizeBytes = copied.sizeBytes,
                durationMs = null,
                originalFilename = null,
                localFullFilePath = null,
            )
        }
    }

    /** Cover photos are always images (never video/document, unlike the general attachments
     * picker) — a narrower sibling of [uploadNewMediaAttachment]'s image branch, uploading under
     * the same `CALENDAR_IMAGE` category so it needs no dedicated media category server-side. */
    private suspend fun uploadNewCoverPhoto(uri: Uri): String {
        val copied = mediaStorage.copyImageToPrivateStorage(uri)
        return mediaStorage.uploadToServer(copied.filePath, copied.mimeType, MediaCategoryDto.CALENDAR_IMAGE).id
    }

    private suspend fun uploadNewDocumentAttachment(uri: Uri): NewCalendarAttachment {
        val copied = mediaStorage.copyDocumentToPrivateStorage(uri)
        val asset = mediaStorage.uploadToServer(
            copied.filePath,
            copied.mimeType,
            MediaCategoryDto.CALENDAR_DOCUMENT,
            originalFilename = copied.originalFilename,
        )
        return NewCalendarAttachment(
            assetId = asset.id,
            kind = MessageAttachmentKind.DOCUMENT,
            filePath = "",
            mimeType = copied.mimeType,
            widthPx = null,
            heightPx = null,
            sizeBytes = copied.sizeBytes,
            durationMs = null,
            originalFilename = copied.originalFilename,
            localFullFilePath = copied.filePath,
        )
    }

    /** Images are downloaded eagerly (see [reconcileAttachments]), so exporting one just copies
     * the already-local file — mirrors `MessageRepository.exportImage` exactly. */
    suspend fun exportEventImage(attachment: MessageAttachment, destinationUri: Uri) =
        mediaStorage.exportToDocument(attachment.filePath, destinationUri)

    /** Mirrors `MessageRepository.downloadVideoIfNeeded` — lazy, on tap of the video viewer. */
    suspend fun downloadEventVideoIfNeeded(eventId: String, attachment: MessageAttachment): String {
        attachment.localVideoFilePath?.let { return it }
        val mediaId = requireNotNull(attachment.mediaId) { "video attachment missing mediaId" }
        val downloaded = mediaStorage.downloadVideoToMediaDir(mediaId, attachment.mimeType)
        calendarAttachmentDao.getForEventAndMedia(eventId, mediaId)?.let {
            calendarAttachmentDao.updateLocalVideoPath(it.id, downloaded.filePath)
        }
        return downloaded.filePath
    }

    /** Mirrors `MessageRepository.downloadDocumentIfNeeded`. */
    suspend fun downloadEventDocumentIfNeeded(eventId: String, attachment: MessageAttachment): String {
        attachment.localDocumentFilePath?.let { return it }
        val mediaId = requireNotNull(attachment.mediaId) { "document attachment missing mediaId" }
        val downloaded = mediaStorage.downloadDocumentToMediaDir(mediaId, attachment.mimeType)
        calendarAttachmentDao.getForEventAndMedia(eventId, mediaId)?.let {
            calendarAttachmentDao.updateLocalDocumentPath(it.id, downloaded.filePath)
        }
        return downloaded.filePath
    }

    suspend fun exportEventVideo(attachment: MessageAttachment, destinationUri: Uri) {
        val localPath = requireNotNull(attachment.localVideoFilePath) { "video not downloaded yet" }
        mediaStorage.exportToDocument(localPath, destinationUri)
    }

    suspend fun exportEventDocument(attachment: MessageAttachment, destinationUri: Uri) {
        val localPath = requireNotNull(attachment.localDocumentFilePath) { "document not downloaded yet" }
        mediaStorage.exportToDocument(localPath, destinationUri)
    }

    suspend fun deleteEvent(id: String) {
        consoleApiCall { calendarApi.delete(id) }
        sweepEventFiles(id)
        calendarDao.delete(id)
    }

    /** The "Delete data" action for Calendar in Settings — wipes the entire shared calendar,
     * matching `calendar_service.wipe_shared_calendar`'s no-ownership-filter scope. */
    suspend fun wipeCalendar() {
        consoleApiCall { calendarApi.wipeAll() }
        sweepAllFiles()
        calendarDao.deleteAll()
    }

    private fun CalendarEventOutDto.toEntity(coverMediaId: String?, coverFilePath: String?) = CalendarEventEntity(
        id = id,
        title = title,
        notes = description,
        type = type.toEventType(),
        startAtEpochMillis = startAt.isoDateTimeToEpochMillis(),
        endAtEpochMillis = endAt?.isoDateTimeToEpochMillis(),
        isAllDay = allDay,
        location = location,
        recurrenceRule = recurrenceRule,
        cancelled = cancelled,
        cancellationReason = cancellationReason,
        cancelledByPartnerId = cancelledBy,
        reminderMinutesBefore = reminderMinutesBefore,
        intimacyRating = intimacy?.rating,
        intimacyDurationMinutes = intimacy?.durationMinutes,
        intimacyInitiatedByPartnerId = intimacy?.initiatedBy,
        intimacyProtectionUsed = intimacy?.protectionUsed,
        intimacyPositions = intimacy?.positions.orEmpty(),
        intimacyLocations = intimacy?.locations.orEmpty(),
        intimacyMoods = intimacy?.moods.orEmpty(),
        intimacyRounds = intimacy?.rounds,
        intimacyOrgasmedByPartnerId = intimacy?.orgasmedBy,
        createdByPartnerId = createdBy,
        createdAtEpochMillis = createdAt.isoDateTimeToEpochMillis(),
        coverMediaId = coverMediaId,
        coverFilePath = coverFilePath,
    )

    private fun CalendarEventWithAttachments.toCalendarItem() = CalendarItem(
        id = event.id,
        title = event.title,
        notes = event.notes,
        type = event.type,
        startAtEpochMillis = event.startAtEpochMillis,
        endAtEpochMillis = event.endAtEpochMillis,
        isAllDay = event.isAllDay,
        location = event.location,
        recurrenceRule = event.recurrenceRule,
        cancelled = event.cancelled,
        cancellationReason = event.cancellationReason,
        cancelledByPartnerId = event.cancelledByPartnerId,
        reminderMinutesBefore = event.reminderMinutesBefore,
        intimacyRating = event.intimacyRating,
        intimacyDurationMinutes = event.intimacyDurationMinutes,
        intimacyInitiatedByPartnerId = event.intimacyInitiatedByPartnerId,
        intimacyProtectionUsed = event.intimacyProtectionUsed,
        intimacyPositions = event.intimacyPositions,
        intimacyLocations = event.intimacyLocations,
        intimacyMoods = event.intimacyMoods,
        intimacyRounds = event.intimacyRounds,
        intimacyOrgasmedByPartnerId = event.intimacyOrgasmedByPartnerId,
        displayAtEpochMillis = computeNextOccurrence(event.startAtEpochMillis, event.recurrenceRule),
        attachments = attachments.map { it.toMessageAttachment() },
        coverPhotoFilePath = event.coverFilePath,
        coverMediaId = event.coverMediaId,
    )

    private fun CalendarAttachmentEntity.toMessageAttachment() = MessageAttachment(
        kind = when (attachmentKind) {
            "VIDEO" -> MessageAttachmentKind.VIDEO
            "DOCUMENT" -> MessageAttachmentKind.DOCUMENT
            else -> MessageAttachmentKind.IMAGE
        },
        filePath = filePath,
        mimeType = mimeType,
        widthPx = widthPx,
        heightPx = heightPx,
        sizeBytes = sizeBytes,
        mediaId = mediaId,
        durationMillis = durationMs?.toLong(),
        localVideoFilePath = localVideoFilePath,
        originalFilename = originalFilename,
        localDocumentFilePath = localDocumentFilePath,
    )

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

    private companion object {
        /** Next occurrence at/after now for the four canned presets (see `RecurrencePreset`), or
         * [startAtEpochMillis] itself when there's no recurrence. Leap-day (Feb 29 yearly)
         * originals roll over to the platform's default `Calendar` behavior (typically Mar 1) in
         * non-leap years — an accepted simplification rather than special-cased. */
        fun computeNextOccurrence(startAtEpochMillis: Long, recurrenceRule: String?): Long {
            val field = when (recurrenceRule) {
                "FREQ=DAILY" -> Calendar.DAY_OF_YEAR
                "FREQ=WEEKLY" -> Calendar.WEEK_OF_YEAR
                "FREQ=MONTHLY" -> Calendar.MONTH
                "FREQ=YEARLY" -> Calendar.YEAR
                else -> return startAtEpochMillis
            }
            val next = Calendar.getInstance().apply { timeInMillis = startAtEpochMillis }
            val now = Calendar.getInstance()
            while (next.before(now)) {
                next.add(field, 1)
            }
            return next.timeInMillis
        }
    }
}
