package com.wwwescape.deviceinfox.console.data.messaging

import android.net.Uri
import com.wwwescape.deviceinfox.console.data.db.AttachmentDao
import com.wwwescape.deviceinfox.console.data.db.AttachmentEntity
import com.wwwescape.deviceinfox.console.data.db.HiddenMessageDao
import com.wwwescape.deviceinfox.console.data.db.HiddenMessageEntity
import com.wwwescape.deviceinfox.console.data.db.MessageDao
import com.wwwescape.deviceinfox.console.data.db.MessageDeliveryState
import com.wwwescape.deviceinfox.console.data.db.MessageEntity
import com.wwwescape.deviceinfox.console.data.db.MessageWithDetails
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.db.PollOptionDao
import com.wwwescape.deviceinfox.console.data.db.PollOptionEntity
import com.wwwescape.deviceinfox.console.data.db.ReactionDao
import com.wwwescape.deviceinfox.console.data.db.ReactionEntity
import com.wwwescape.deviceinfox.console.data.db.VoiceNoteDao
import com.wwwescape.deviceinfox.console.data.db.VoiceNoteEntity
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.MessagesApi
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.MediaCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageTypeDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PollCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PollOptionCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PollVoteUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.ReactionCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateTimeString
import com.wwwescape.deviceinfox.console.data.presence.PresenceRepository
import com.wwwescape.deviceinfox.console.push.ConsolePushNotifier
import com.wwwescape.deviceinfox.console.push.PushCategory
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val hiddenMessageDao: HiddenMessageDao,
    private val reactionDao: ReactionDao,
    private val attachmentDao: AttachmentDao,
    private val voiceNoteDao: VoiceNoteDao,
    private val pollOptionDao: PollOptionDao,
    private val partnerRepository: PartnerRepository,
    private val mediaStorage: MediaStorage,
    private val messagesApi: MessagesApi,
    private val webSocketClient: ConsoleWebSocketClient,
    private val json: Json,
    private val presenceRepository: PresenceRepository,
    private val pushNotifier: ConsolePushNotifier,
) {
    /** Same reasoning as `ServerPairingRepository`'s scope — kicks off the initial history sync
     * the moment this singleton is created, rather than only reacting to a Room `Flow` that's
     * always immediately available. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            // If Room already has messages cached (true for every cold start except a fresh
            // pairing/install), there's no reason to keep the blocking "Loading…" screen up while
            // syncConversation() talks to the network — `conversation` below already has real,
            // renderable data this instant, and sync can happen invisibly in the background the
            // same way a WS-reconnect resync already does. Only a genuinely empty local cache has
            // to wait for the first fetch, since there's nothing else to show meanwhile.
            if (messageDao.newestMessageTimestamp() != null) {
                _isInitialSyncing.value = false
            }
            // Always cleared, success or failure — this only gates a one-time "Loading…" screen
            // on cold start, not a retry mechanism, so a network failure shouldn't strand the UI
            // blocked forever.
            try {
                syncConversation()
            } finally {
                _isInitialSyncing.value = false
            }
        }
        // Each event is individually caught — collect() would otherwise stop processing every
        // later event the moment a single malformed one threw.
        scope.launch { webSocketClient.events.collect { event -> runCatching { handleWsEvent(event) } } }
        // Catches up on anything sent while this device's console was closed (the WebSocket
        // wasn't connected to receive it live) — fires on the very first connect and every
        // reconnect after, see ConsoleWebSocketClient.connected's doc comment.
        scope.launch {
            webSocketClient.connected.collect { runCatching { syncConversation(); markIncomingAsDelivered() } }
        }
    }

    private val _isInitialSyncing = MutableStateFlow(true)

    /** True only for the very first [syncConversation] on cold start (construction) — reconnect
     * resyncs from `webSocketClient.connected` never touch this, so the blocking "Loading…" UI
     * this backs only ever appears once per session, not on every reconnect. */
    val isInitialSyncing: StateFlow<Boolean> = _isInitialSyncing.asStateFlow()

    private val _conversationWipedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires only when the *partner's* device wipes the conversation (the WS-delivered side of
     * [wipeConversation]) — this device's own trigger doesn't need to tell itself anything, the
     * Settings screen already has its own success/failure feedback for that. */
    val conversationWipedEvent: SharedFlow<Unit> = _conversationWipedEvent.asSharedFlow()

    val conversation: Flow<List<ConsoleMessage>> = combine(
        messageDao.observeConversation(),
        partnerRepository.selfProfile,
        hiddenMessageDao.observeAllHiddenIds(),
    ) { messages, self, hiddenIds ->
        val hiddenIdSet = hiddenIds.toSet()
        messages.map { it.toConsoleMessage(selfPartnerId = self?.id, hiddenIds = hiddenIdSet) }
    }

    /** Backs the Starred messages screen. [MessageDao.observeStarred] already excludes a real
     * delete (`isDeleted = 0`), but not a local-only "Delete for me" hide — that's a separate
     * table this query can't see, so the extra `!it.isDeleted` filter here (post-`toConsoleMessage`,
     * which folds [hiddenIds] into that same flag) is what actually drops those, same as it would
     * render as a tombstone in the main conversation. */
    val starredMessages: Flow<List<ConsoleMessage>> = combine(
        messageDao.observeStarred(),
        partnerRepository.selfProfile,
        hiddenMessageDao.observeAllHiddenIds(),
    ) { messages, self, hiddenIds ->
        val hiddenIdSet = hiddenIds.toSet()
        messages.map { it.toConsoleMessage(selfPartnerId = self?.id, hiddenIds = hiddenIdSet) }.filter { !it.isDeleted }
    }

    /** Backs the Pinned messages screen — same shape/tombstone-exclusion as [starredMessages]. */
    val pinnedMessages: Flow<List<ConsoleMessage>> = combine(
        messageDao.observePinned(),
        partnerRepository.selfProfile,
        hiddenMessageDao.observeAllHiddenIds(),
    ) { messages, self, hiddenIds ->
        val hiddenIdSet = hiddenIds.toSet()
        messages.map { it.toConsoleMessage(selfPartnerId = self?.id, hiddenIds = hiddenIdSet) }.filter { !it.isDeleted }
    }

    /** Backs the "shared media" grid opened by tapping the partner's name in the conversation
     * header (`HomeScreen.kt`'s `TopAppBar` title) — every message with a non-null
     * [ConsoleMessage.attachment], newest first (the opposite of [conversation]'s chronological
     * order), matching how WhatsApp/Signal order their own media grids. Derived from [conversation]
     * rather than a new DAO query, since [conversation] already folds in the same self/hiddenIds/
     * tombstone logic every other screen here relies on — no reason to duplicate it. */
    val conversationMedia: Flow<List<ConsoleMessage>> = conversation.map { messages ->
        messages.filter { it.attachment != null && !it.isDeleted }.sortedByDescending { it.sentAtEpochMillis }
    }

    /** Runs on construction and again on every `webSocketClient.connected` — the WebSocket's own
     * doc comment covers why a resync is needed on every reconnect, not just once: a message
     * sent while this device's console was closed reaches neither channel otherwise.
     *
     * Two bounded, cheap steps once there's already a local cache to build on — replacing what
     * used to be an unconditional walk back through up to [MAX_SYNC_PAGES] worth of history (500
     * messages) *from scratch on every single sync*, whether or not anything had actually changed:
     *  - [syncNewMessages]: anything created after the newest message already cached locally, an
     *    `after=`-bounded walk forward with no fixed page cap (there's no way to know in advance
     *    how many might be waiting, though in practice it's almost always well under one page).
     *  - [syncRecentTopUp]: a single most-recent page re-fetched by `before=`, to catch edits/
     *    pins/stars/reactions on messages *already* cached locally that changed while this device
     *    was offline — `after=` alone can't see those, since none of those change a message's
     *    `created_at`. Deliberately bounded to one page rather than [MAX_SYNC_PAGES]: a change to
     *    something older than the most recent ~100 messages is a rare enough edge case that it's
     *    not worth a heavier fetch on every single sync for it — the old fixed 5-page walk never
     *    reliably covered arbitrarily-old history either, just less rarely.
     *
     * Falls back to a real, [MAX_SYNC_PAGES]-bounded full history walk ([syncFullHistory]) only
     * when there's no local cache at all yet (fresh pairing / fresh install) — there's nothing
     * "recent" to top up in that case, and this is the one situation that actually needs real
     * initial history from the server.
     *
     * Soft-deleted messages are silently excluded by the server's own query in every case above
     * (`list_messages` filters `deleted_at IS NULL`) — a delete the *partner* made while this
     * device wasn't connected is never included in any REST response, so it's only ever reflected
     * locally via a live `message.deleted` WS event, not by this resync (a pre-existing gap, not
     * something this change affects either way). This device's own deletes are applied locally
     * immediately regardless (see [deleteMessage]). */
    suspend fun syncConversation() {
        partnerRepository.ensureSelfProfileExists()
        val newestLocalEpochMillis = messageDao.newestMessageTimestamp()
        if (newestLocalEpochMillis == null) {
            syncFullHistory()
            return
        }
        syncNewMessages(newestLocalEpochMillis)
        syncRecentTopUp()
    }

    private suspend fun syncFullHistory() {
        var before: String? = null
        repeat(MAX_SYNC_PAGES) {
            val page = runCatching { messagesApi.list(before = before, limit = PAGE_SIZE) }.getOrNull() ?: return
            if (page.isEmpty()) return
            applyRemoteMessagesInOrder(page)
            before = page.minOf { it.createdAt }
            if (page.size < PAGE_SIZE) return
        }
    }

    private suspend fun syncNewMessages(sinceEpochMillis: Long) {
        var after = sinceEpochMillis.toIsoDateTimeString()
        while (true) {
            val page = runCatching { messagesApi.list(after = after, limit = PAGE_SIZE) }.getOrNull() ?: return
            if (page.isEmpty()) return
            applyRemoteMessagesInOrder(page)
            after = page.maxOf { it.createdAt }
            if (page.size < PAGE_SIZE) return
        }
    }

    private suspend fun syncRecentTopUp() {
        val page = runCatching { messagesApi.list(limit = PAGE_SIZE) }.getOrNull() ?: return
        applyRemoteMessagesInOrder(page)
    }

    /** One "load earlier" page, resumable from wherever local history currently ends (Room's own
     * oldest cached row), rather than [syncConversation]'s fixed cap starting over from "now" —
     * see `HomeViewModel.loadOlderMessages`, triggered by scrolling to the top of the Messages
     * list. Unlike [syncConversation]'s per-page `runCatching`, a failed request here throws
     * normally so the caller's own error handling (a Toast) surfaces it, rather than failing
     * silently. Returns whether a full page came back — a short/empty page means local history
     * has caught up with the start of the conversation. */
    suspend fun loadOlderMessages(): Boolean {
        val oldestEpochMillis = messageDao.oldestMessageTimestamp() ?: return false
        val page = messagesApi.list(before = oldestEpochMillis.toIsoDateTimeString(), limit = PAGE_SIZE)
        applyRemoteMessagesInOrder(page)
        return page.size >= PAGE_SIZE
    }

    /** [MessageEntity.replyToId] is a self-referencing foreign key onto `messages.id` — so a
     * reply can only be upserted once the message it replies to already exists locally. Both
     * [syncConversation] and [loadOlderMessages] fetch pages newest-first (the `before=` cursor
     * walk), which is the wrong order for that constraint: within a page, a reply almost always
     * sits *before* the message it replies to (replies are, by definition, never older than their
     * parent). Sorting each page oldest-first before applying fixes the common case outright.
     *
     * It can't fix every case, though — a reply whose parent fell on an *earlier, not-yet-fetched*
     * page (or outside [MAX_SYNC_PAGES] entirely) still has no local parent to satisfy the
     * constraint no matter what order this page is processed in. [runCatching] around each
     * individual apply is what actually prevents a crash there: previously, one such message threw
     * an uncaught `SQLiteConstraintException` out of the whole `forEach`, which — since every
     * caller of [syncConversation]/[loadOlderMessages] is itself fire-and-forget with no exception
     * handler of its own (`init`'s `scope.launch { }`, the WebSocket reconnect collector) — crashed
     * the entire app, reliably, on every single console open once any reply existed in the
     * conversation at all. A message that fails to apply here just stays out of date until a later
     * resync (its parent will very likely exist locally by then), the same "best-effort, retried
     * next time" tradeoff already made for the page fetch itself failing outright. */
    private suspend fun applyRemoteMessagesInOrder(page: List<MessageOutDto>) {
        page.sortedBy { it.createdAt }.forEach { dto -> runCatching { applyRemoteMessage(dto) } }
    }

    /** Inserts a local, [MessageDeliveryState.SENDING] row immediately — keyed by the same id
     * sent as `client_message_id` — so the message shows up in the conversation the instant it's
     * submitted rather than only once the server round-trip completes (previously the composer
     * looked like it had done nothing until the network call finished). On success the pending
     * row is replaced by the real, server-assigned one; on failure it's left in place but marked
     * [MessageDeliveryState.FAILED] so the UI can show a "not sent" indicator instead of the
     * message just vanishing. */
    suspend fun sendText(body: String, replyToId: String?) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        val pendingId = UUID.randomUUID().toString()
        messageDao.insert(
            MessageEntity(
                id = pendingId,
                senderId = selfId,
                body = body,
                replyToId = replyToId,
                sentAtEpochMillis = System.currentTimeMillis(),
                editedAtEpochMillis = null,
                deliveryState = MessageDeliveryState.SENDING,
            ),
        )
        try {
            val response = consoleApiCall {
                messagesApi.send(
                    MessageCreateDto(
                        type = MessageTypeDto.TEXT,
                        body = body,
                        replyToId = replyToId,
                        clientMessageId = pendingId,
                    ),
                )
            }
            messageDao.deleteById(pendingId)
            applyRemoteMessage(response)
        } catch (e: Exception) {
            messageDao.setDeliveryState(pendingId, MessageDeliveryState.FAILED)
            throw e
        }
    }

    suspend fun sendImage(sourceUri: Uri, replyToId: String?) {
        partnerRepository.ensureSelfProfileExists()
        val copied = mediaStorage.copyImageToPrivateStorage(sourceUri)
        val asset = mediaStorage.uploadToServer(copied.filePath, copied.mimeType, MediaCategoryDto.MESSAGE_IMAGE)
        val response = consoleApiCall {
            messagesApi.send(
                MessageCreateDto(
                    type = MessageTypeDto.IMAGE,
                    mediaId = asset.id,
                    replyToId = replyToId,
                    clientMessageId = UUID.randomUUID().toString(),
                ),
            )
        }
        messageDao.upsert(response.toMessageEntity())
        attachmentDao.insert(
            AttachmentEntity(
                id = UUID.randomUUID().toString(),
                messageId = response.id,
                attachmentKind = "IMAGE",
                filePath = copied.filePath,
                mimeType = copied.mimeType,
                widthPx = copied.widthPx,
                heightPx = copied.heightPx,
                sizeBytes = copied.sizeBytes,
            ),
        )
    }

    suspend fun saveImageToPrivateDownloads(attachment: MessageAttachment): File =
        mediaStorage.saveToPrivateDownloads(attachment.filePath, attachment.mimeType)

    /** [attachment.filePath] is only ever the poster thumbnail for a video — the real bytes to
     * save live at [MessageAttachment.localVideoFilePath], which the caller must ensure is
     * non-null first (see `HomeViewModel.ensureVideoDownloaded`); the video viewer only ever
     * offers this once playback has already required a local copy to exist. */
    suspend fun saveVideoToPrivateDownloads(attachment: MessageAttachment): File {
        val localPath = requireNotNull(attachment.localVideoFilePath) { "video not downloaded yet" }
        return mediaStorage.saveToPrivateDownloads(localPath, attachment.mimeType)
    }

    suspend fun sendVoiceNote(filePath: String, durationMillis: Long, replyToId: String?) {
        partnerRepository.ensureSelfProfileExists()
        val asset = mediaStorage.uploadToServer(
            filePath,
            "audio/mp4",
            MediaCategoryDto.MESSAGE_VOICE,
            durationMs = durationMillis.toInt(),
        )
        val response = consoleApiCall {
            messagesApi.send(
                MessageCreateDto(
                    type = MessageTypeDto.VOICE,
                    mediaId = asset.id,
                    replyToId = replyToId,
                    clientMessageId = UUID.randomUUID().toString(),
                ),
            )
        }
        messageDao.upsert(response.toMessageEntity())
        voiceNoteDao.insert(
            VoiceNoteEntity(id = UUID.randomUUID().toString(), messageId = response.id, filePath = filePath, durationMillis = durationMillis),
        )
    }

    /** Combines [sendImage]'s "copy the picked/recorded file locally, then upload" shape with
     * [sendVoiceNote]'s "duration is probed client-side and passed through" shape, plus a second
     * upload for the poster-frame thumbnail (server never generates one — no ffmpeg dependency,
     * see `media_service.py`'s own comment). The thumbnail is uploaded *first* so its asset id
     * can be attached to the video asset's own upload (`thumbnailMediaId`). Unlike [sendImage],
     * the sender's own [AttachmentEntity.localVideoFilePath] is populated immediately — they
     * already have the full file on disk, no reason to make them re-download their own send. */
    suspend fun sendVideo(sourceUri: Uri, replyToId: String?) {
        partnerRepository.ensureSelfProfileExists()
        val copied = mediaStorage.copyVideoToPrivateStorage(sourceUri)
        val probe = mediaStorage.probeVideoDurationAndThumbnail(copied.filePath)
        val thumbnailAsset = probe.thumbnailFilePath?.let { thumbPath ->
            mediaStorage.uploadToServer(thumbPath, "image/jpeg", MediaCategoryDto.MESSAGE_IMAGE)
        }
        val videoAsset = mediaStorage.uploadToServer(
            copied.filePath,
            copied.mimeType,
            MediaCategoryDto.MESSAGE_VIDEO,
            durationMs = probe.durationMs,
            thumbnailMediaId = thumbnailAsset?.id,
        )
        val response = consoleApiCall {
            messagesApi.send(
                MessageCreateDto(
                    type = MessageTypeDto.VIDEO,
                    mediaId = videoAsset.id,
                    replyToId = replyToId,
                    clientMessageId = UUID.randomUUID().toString(),
                ),
            )
        }
        messageDao.upsert(response.toMessageEntity())
        attachmentDao.insert(
            AttachmentEntity(
                id = UUID.randomUUID().toString(),
                messageId = response.id,
                attachmentKind = "VIDEO",
                filePath = probe.thumbnailFilePath ?: copied.filePath,
                mimeType = copied.mimeType,
                widthPx = probe.thumbnailWidthPx,
                heightPx = probe.thumbnailHeightPx,
                sizeBytes = copied.sizeBytes,
                mediaId = videoAsset.id,
                durationMs = probe.durationMs,
                localVideoFilePath = copied.filePath,
            ),
        )
    }

    /** No-ops if [attachment]'s video bytes are already local (the sender's own row, or a
     * receiver who already tapped play once this session) — otherwise fetches them lazily via
     * [MediaStorage.downloadVideoToMediaDir], the one deliberate divergence from image/voice's
     * eager-download-on-receipt pattern (videos can be up to 150MB). */
    suspend fun downloadVideoIfNeeded(messageId: String, attachment: MessageAttachment): String {
        attachment.localVideoFilePath?.let { return it }
        val mediaId = requireNotNull(attachment.mediaId) { "video attachment missing mediaId" }
        val downloaded = mediaStorage.downloadVideoToMediaDir(mediaId, attachment.mimeType)
        attachmentDao.getForMessage(messageId)?.let { attachmentDao.updateLocalVideoPath(it.id, downloaded.filePath) }
        return downloaded.filePath
    }

    /** Simpler than [sendVideo] — a document has no preview/thumbnail to generate, just a single
     * upload with the picked file's real name attached. */
    suspend fun sendDocument(sourceUri: Uri, replyToId: String?) {
        partnerRepository.ensureSelfProfileExists()
        val copied = mediaStorage.copyDocumentToPrivateStorage(sourceUri)
        val asset = mediaStorage.uploadToServer(
            copied.filePath,
            copied.mimeType,
            MediaCategoryDto.MESSAGE_DOCUMENT,
            originalFilename = copied.originalFilename,
        )
        val response = consoleApiCall {
            messagesApi.send(
                MessageCreateDto(
                    type = MessageTypeDto.DOCUMENT,
                    mediaId = asset.id,
                    replyToId = replyToId,
                    clientMessageId = UUID.randomUUID().toString(),
                ),
            )
        }
        messageDao.upsert(response.toMessageEntity())
        attachmentDao.insert(
            AttachmentEntity(
                id = UUID.randomUUID().toString(),
                messageId = response.id,
                attachmentKind = "DOCUMENT",
                filePath = "",
                mimeType = copied.mimeType,
                widthPx = null,
                heightPx = null,
                sizeBytes = copied.sizeBytes,
                mediaId = asset.id,
                originalFilename = copied.originalFilename,
                // The sender already has the file — no reason to make them re-download their own
                // send, same reasoning as sendVideo's localVideoFilePath.
                localDocumentFilePath = copied.filePath,
            ),
        )
    }

    /** Mirrors [downloadVideoIfNeeded] — a document can be arbitrarily large, so it's never
     * downloaded eagerly on receipt, only lazily when the Save button is tapped. */
    suspend fun downloadDocumentIfNeeded(messageId: String, attachment: MessageAttachment): String {
        attachment.localDocumentFilePath?.let { return it }
        val mediaId = requireNotNull(attachment.mediaId) { "document attachment missing mediaId" }
        val downloaded = mediaStorage.downloadDocumentToMediaDir(mediaId, attachment.mimeType)
        attachmentDao.getForMessage(messageId)?.let { attachmentDao.updateLocalDocumentPath(it.id, downloaded.filePath) }
        return downloaded.filePath
    }

    /** Mirrors [saveVideoToPrivateDownloads] — the caller (`HomeViewModel.saveDocument`) must
     * ensure [MessageAttachment.localDocumentFilePath] is non-null first (via
     * [downloadDocumentIfNeeded]). Passes [MessageAttachment.originalFilename] through so the
     * saved copy keeps a recognizable name rather than a random one. */
    suspend fun saveDocumentToPrivateDownloads(attachment: MessageAttachment): File {
        val localPath = requireNotNull(attachment.localDocumentFilePath) { "document not downloaded yet" }
        return mediaStorage.saveToPrivateDownloads(localPath, attachment.mimeType, attachment.originalFilename)
    }

    suspend fun editText(messageId: String, newBody: String) {
        val response = consoleApiCall { messagesApi.edit(messageId, MessageUpdateDto(newBody)) }
        applyRemoteMessage(response)
    }

    /** No optimistic local insert, unlike [sendText] — matches [sendVideo]/[sendDocument]'s own
     * "wait for the real response" shape rather than [sendText]'s SENDING-placeholder dance,
     * since a poll has no obvious partial state worth showing immediately either. [question]
     * is carried as the message's own [MessageCreateDto.body], same as every other message
     * type's text — polls were never given a separate "title" field. */
    suspend fun sendPoll(question: String, options: List<String>, allowsMultiple: Boolean, replyToId: String?) {
        partnerRepository.ensureSelfProfileExists()
        val response = consoleApiCall {
            messagesApi.send(
                MessageCreateDto(
                    type = MessageTypeDto.POLL,
                    body = question,
                    replyToId = replyToId,
                    clientMessageId = UUID.randomUUID().toString(),
                    poll = PollCreateDto(
                        options = options.map { PollOptionCreateDto(text = it) },
                        allowsMultiple = allowsMultiple,
                    ),
                ),
            )
        }
        applyRemoteMessage(response)
    }

    /** [optionIds] is this device's complete current selection, not an incremental toggle — see
     * `PollVoteUpdateDto`'s own doc comment. Passing an empty list is a valid, ordinary way to
     * retract every vote. */
    suspend fun votePoll(messageId: String, optionIds: List<String>) {
        val response = consoleApiCall { messagesApi.votePoll(messageId, PollVoteUpdateDto(optionIds)) }
        applyRemoteMessage(response)
    }

    /** Poll creator only — the server 403s otherwise (`ForbiddenError`), surfaced the same way
     * every other [consoleApiCall] failure is. */
    suspend fun closePoll(messageId: String) {
        val response = consoleApiCall { messagesApi.closePoll(messageId) }
        applyRemoteMessage(response)
    }

    suspend fun deleteMessage(messageId: String) {
        consoleApiCall { messagesApi.delete(messageId) }
        messageDao.softDelete(messageId)
        deleteLocalMediaFiles(messageId)
    }

    /** "Delete for me" — purely local, works on ANY message (yours or your partner's), never
     * touches the server or notifies the partner in any way. Renders with the exact same
     * tombstone [deleteMessage] produces (see [toConsoleMessage]'s `hiddenIds` check), but the
     * real content stays intact in the partner's copy and in this device's own DB — only the
     * `hidden_messages` marker row is new. */
    suspend fun hideForMe(messageId: String) {
        hiddenMessageDao.hide(HiddenMessageEntity(messageId))
        deleteLocalMediaFiles(messageId)
    }

    /** Bulk version, for multi-select. */
    suspend fun hideForMeBulk(messageIds: List<String>) {
        hiddenMessageDao.hideAll(messageIds.map { HiddenMessageEntity(it) })
        messageIds.forEach { deleteLocalMediaFiles(it) }
    }

    /** Frees the attachment/voice-note files a deleted (or "delete for me"-hidden) message
     * pointed at — previously left dangling forever, since only the full "Delete data" wipe
     * ([clearLocalConversation]) ever actually cleaned up files; a single-message delete only
     * ever flipped a flag or added a hide-marker, never touched the file on disk. Safe to do
     * unconditionally: "Save to Vault" and the explicit Download action both already write
     * independent physical copies (see [MediaStorage.saveToPrivateDownloads]'s own doc comment on
     * why that storage is separate) — this can never touch either. Leaves the
     * [AttachmentEntity]/[VoiceNoteEntity] DB rows themselves in place (mirrors [hideForMe]'s own
     * "the message row stays intact" behavior) — only the on-disk bytes are freed. Called both
     * from this device's own delete/hide actions and from the `"message.deleted"` WS handler
     * below, since the partner deleting a message means this device's local copy is just as
     * stale as it would be after deleting it locally. */
    private suspend fun deleteLocalMediaFiles(messageId: String) {
        attachmentDao.getForMessage(messageId)?.let { attachment ->
            listOfNotNull(
                attachment.filePath.takeIf { it.isNotBlank() },
                attachment.localVideoFilePath,
                attachment.localDocumentFilePath,
            ).forEach { mediaStorage.deleteFile(it) }
        }
        voiceNoteDao.getForMessage(messageId)?.let { mediaStorage.deleteFile(it.filePath) }
    }

    /** The "Delete data" action for Messages in Settings — a genuine hard wipe of the entire
     * conversation for both partners, mirrors `message_service.wipe_conversation` on the
     * server. The existing single-message [deleteMessage] is unchanged (still sender-scoped
     * soft-delete with a tombstone) — this only affects the bulk action. */
    suspend fun wipeConversation() {
        consoleApiCall { messagesApi.deleteAllMine() }
        clearLocalConversation()
    }

    /** Removes every message row (Room's `@ForeignKey(onDelete = CASCADE)` on
     * [ReactionEntity]/[AttachmentEntity]/[VoiceNoteEntity] cleans those automatically) plus the
     * attachment/voice-note files those rows pointed at, which the cascade can't touch — files
     * have to be snapshotted *before* the delete or their paths are gone. Shared by this
     * device's own [wipeConversation] trigger and the `"message.bulk_deleted"` WS handler below,
     * since the partner's device needs the exact same local cleanup. */
    private suspend fun clearLocalConversation() {
        val attachmentSnapshot = attachmentDao.getAllSnapshot()
        // filePath covers the image case and (for video) the thumbnail, but is unused (blank)
        // for document rows — localVideoFilePath/localDocumentFilePath are each a second,
        // separate file on disk that only some rows even have, so they need their own
        // null-filtered passes rather than being folded into the same .map { it.filePath }.
        val filePaths = attachmentSnapshot.map { it.filePath }.filter { it.isNotBlank() } +
            attachmentSnapshot.mapNotNull { it.localVideoFilePath } +
            attachmentSnapshot.mapNotNull { it.localDocumentFilePath } +
            voiceNoteDao.getAllSnapshot().map { it.filePath }
        messageDao.deleteAll()
        hiddenMessageDao.deleteAll()
        filePaths.forEach { mediaStorage.deleteFile(it) }
    }

    /** The Messages header icon's "I'm online" ping — a manual, one-off push telling the partner
     * this device is available right now, distinct from the automatic online/last-seen presence
     * already shown under their name (see `HomeScreen.presenceSubtitle`). Purely a fire-and-forget
     * network call: nothing local to update, since this device isn't the one whose state changed.
     * Cooldown enforcement (15 minutes) is the server's job (`ConsoleApiException.httpStatusCode
     * == 429`); the caller is responsible for not re-enabling its own button before then. */
    suspend fun sendOnlinePing() {
        consoleApiCall { messagesApi.notifyOnline() }
    }

    suspend fun setStarred(messageId: String, starred: Boolean) {
        if (starred) consoleApiCall { messagesApi.star(messageId) } else consoleApiCall { messagesApi.unstar(messageId) }
        messageDao.setStarred(messageId, starred)
    }

    suspend fun setPinned(messageId: String, pinned: Boolean) {
        val response = if (pinned) consoleApiCall { messagesApi.pin(messageId) } else consoleApiCall { messagesApi.unpin(messageId) }
        applyRemoteMessage(response)
    }

    suspend fun setReaction(messageId: String, emoji: String) {
        val response = consoleApiCall { messagesApi.addReaction(messageId, ReactionCreateDto(emoji)) }
        applyRemoteMessage(response)
    }

    suspend fun removeReaction(messageId: String) {
        consoleApiCall { messagesApi.removeReaction(messageId) }
        reactionDao.delete(messageId, partnerRepository.ensureSelfProfileExists())
    }

    /** Called when the conversation screen opens — marks every currently-unread incoming
     * message read individually (`POST /messages/{id}/read`), matching the server's per-message
     * read-receipt model rather than the single bulk local update this used before Phase 11. */
    suspend fun markIncomingAsRead() {
        val selfId = partnerRepository.ensureSelfProfileExists()
        val unreadIds = messageDao.observeConversation().first()
            .filter { it.message.senderId != selfId && !it.message.isRead }
            .map { it.message.id }
        unreadIds.forEach { messageId ->
            runCatching { consoleApiCall { messagesApi.markRead(messageId) } }
                .onSuccess { applyRemoteMessage(it) }
        }
    }

    /** Sweeps every locally-undelivered incoming message and acks it (`POST
     * /messages/{id}/delivered`) — covers messages that only ever reached this device through a
     * REST sync (e.g. sent while this device's console was closed) rather than the live
     * `message.new` WebSocket event, which acks inline via [ackDeliveryIfIncoming]. Run on every
     * `webSocketClient.connected`, right after [syncConversation] repopulates the local table. */
    private suspend fun markIncomingAsDelivered() {
        val selfId = partnerRepository.ensureSelfProfileExists()
        val undeliveredIds = messageDao.observeConversation().first()
            .filter { it.message.senderId != selfId && it.message.deliveryState != MessageDeliveryState.DELIVERED }
            .map { it.message.id }
        undeliveredIds.forEach { messageId ->
            runCatching { consoleApiCall { messagesApi.markDelivered(messageId) } }
                .onSuccess { applyRemoteMessage(it) }
        }
    }

    /** Live counterpart to [markIncomingAsDelivered] — acks a just-received `message.new` inline
     * instead of waiting for the next reconnect sweep, so the sender sees the delivered tick
     * without a round-trip delay while the partner's device is actually online. No-op for a
     * message this device sent itself, or one that's already delivered. */
    private suspend fun ackDeliveryIfIncoming(dto: MessageOutDto) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        if (dto.senderId != selfId && dto.deliveredAt == null) {
            runCatching { consoleApiCall { messagesApi.markDelivered(dto.id) } }
                .onSuccess { applyRemoteMessage(it) }
        }
    }

    /** `notification_service.notify_user` (server) only sends an FCM push when it *couldn't*
     * deliver the WS event live — a message delivered live (this device's WebSocket connection is
     * up, the common case whenever the console has any live connection at all, not just when it's
     * actually foregrounded) would otherwise show no notification whatsoever, since only
     * [com.wwwescape.deviceinfox.console.push.ConsoleFcmService] used to know how to show one. This
     * is the live-path counterpart, using the exact same [ConsolePushNotifier] so wording/channel/
     * unseen-count behavior is identical regardless of which path actually delivered the message.
     *
     * No-ops for a message this device sent itself (its own `message.new` echo, same check
     * [ackDeliveryIfIncoming] already makes), and for one whose conversation is already on screen
     * ([PresenceRepository.isSelfInMessagesScreen]) — matching WhatsApp/Signal's own "no
     * notification for the chat you're already looking at" behavior, and mirroring the "Here"
     * presence tier's exact same signal (just read locally here instead of relayed to the
     * partner).
     *
     * `message.new` only — deliberately *not* called for `message.updated` (edits), nor
     * `message.deleted`/`message.bulk_deleted`. An earlier version of this function covered
     * `message.updated` too, on the reasoning that the server's own `fcm_data` on an edit already
     * notified via the FCM path, so leaving this live path silent would be an inconsistency. That
     * turned out to be the wrong fix for the wrong problem: an edit or delete showing the exact
     * same "New message" wording as a genuinely new message reads as a false/"ghost" notification
     * — nothing new to find when the chat is opened. The server-side fix (edit/delete/bulk-delete
     * now all pass `fcm_data=None`, matching `mark_delivered`/`mark_read`'s existing shape) closes
     * that gap from the other side instead, so this function no longer needs to chase it — it's
     * back to covering only the one case that's actually a new message. */
    private suspend fun notifyIfIncomingAndNotOnScreen(dto: MessageOutDto) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        if (dto.senderId == selfId) return
        if (presenceRepository.isSelfInMessagesScreen.first()) return
        pushNotifier.showCategoryNotification(PushCategory.MESSAGE)
    }

    /** Every case's payload shape mirrors exactly what its corresponding `notify_user(...)` call
     * sends in `message_service.py` — `message.new`/`message.updated` carry a full serialized
     * `MessageOut` under `"message"`; everything else only carries the ids involved, since the
     * server never re-serializes a whole message just to report a pin/star/reaction/read
     * change. */
    private suspend fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "message.new" -> {
                val messageJson = event.data["message"] ?: return
                val dto = json.decodeFromJsonElement<MessageOutDto>(messageJson)
                applyRemoteMessage(dto)
                ackDeliveryIfIncoming(dto)
                notifyIfIncomingAndNotOnScreen(dto)
            }
            "message.updated" -> {
                val messageJson = event.data["message"] ?: return
                val dto = json.decodeFromJsonElement<MessageOutDto>(messageJson)
                applyRemoteMessage(dto)
            }
            // Same full-MessageOut-payload shape as message.updated (see message_service.vote_poll/
            // close_poll) — a vote change can touch several options at once (multi-select) or move
            // a vote from one option to another (single-select), which doesn't reduce to a clean
            // single-field delta the way a reaction add/remove does. Deliberately no
            // notifyIfIncomingAndNotOnScreen here, unlike message.new/message.updated — that check
            // is keyed on dto.senderId, which for a poll is always its original creator, never
            // whoever just voted/closed it, so it can't actually tell "my partner just did this"
            // apart from "I just did this myself" the way it correctly can for a real edit. No
            // notification for poll activity is the safer default until that's worth solving.
            "message.poll_voted", "message.poll_closed" -> {
                val messageJson = event.data["message"] ?: return
                val dto = json.decodeFromJsonElement<MessageOutDto>(messageJson)
                applyRemoteMessage(dto)
            }
            "message.deleted" -> {
                val messageId = event.data.messageId() ?: return
                messageDao.softDelete(messageId)
                deleteLocalMediaFiles(messageId)
            }
            "message.bulk_deleted" -> {
                clearLocalConversation()
                _conversationWipedEvent.tryEmit(Unit)
            }
            "message.pinned", "message.unpinned" -> {
                messageDao.setPinned(event.data.messageId() ?: return, event.type == "message.pinned")
            }
            "message.starred", "message.unstarred" -> {
                messageDao.setStarred(event.data.messageId() ?: return, event.type == "message.starred")
            }
            "message.reaction.added" -> {
                val messageId = event.data.messageId() ?: return
                val userId = event.data["user_id"]?.jsonPrimitive?.content ?: return
                val emoji = event.data["emoji"]?.jsonPrimitive?.content ?: return
                reactionDao.upsert(
                    ReactionEntity(messageId = messageId, partnerId = userId, emoji = emoji, reactedAtEpochMillis = System.currentTimeMillis()),
                )
            }
            "message.reaction.removed" -> {
                val messageId = event.data.messageId() ?: return
                val userId = event.data["user_id"]?.jsonPrimitive?.content ?: return
                reactionDao.delete(messageId, userId)
            }
            "message.read" -> {
                val messageId = event.data.messageId() ?: return
                val readAt = event.data["read_at"]?.jsonPrimitive?.content ?: return
                messageDao.setRead(messageId, readAt.isoDateTimeToEpochMillis())
            }
            "message.delivered" -> {
                val messageId = event.data.messageId() ?: return
                val deliveredAt = event.data["delivered_at"]?.jsonPrimitive?.content ?: return
                messageDao.setDelivered(messageId, deliveredAt.isoDateTimeToEpochMillis())
            }
            "message.link_preview_ready" -> {
                val messageId = event.data.messageId() ?: return
                val previewJson = event.data["link_preview"] ?: return
                val preview = json.decodeFromJsonElement<LinkPreviewWsDto>(previewJson)
                val imagePath = preview.mediaId?.let { mediaId ->
                    runCatching { mediaStorage.downloadToMediaDir(mediaId, isVoice = false) }.getOrNull()?.filePath
                }
                messageDao.setLinkPreview(
                    id = messageId,
                    url = preview.url,
                    title = preview.title,
                    description = preview.description,
                    imagePath = imagePath,
                )
            }
        }
    }

    private fun JsonObject.messageId(): String? = this["message_id"]?.jsonPrimitive?.content

    private suspend fun applyRemoteMessage(dto: MessageOutDto) {
        // A local SENDING/FAILED placeholder from sendText() is keyed by client_message_id (the
        // id sendText generated before the send even started), never by the server-assigned
        // dto.id. sendText's own success path already deletes it — but if that attempt's HTTP
        // response was lost after the server had already created the message (dropped connection
        // mid-round-trip), the placeholder is stuck FAILED forever, showing as a permanent "Not
        // sent" ghost bubble once the real message shows up here later via a resync/WS event as a
        // second, separate row. Cleaning it up here, keyed off the id every applyRemoteMessage
        // caller already has, is the one place that reconciliation can happen for every path
        // (send, sync, WS) at once.
        if (dto.clientMessageId != dto.id) {
            messageDao.deleteById(dto.clientMessageId)
        }

        // Read before the upsert below overwrites it — an @Upsert replaces the whole row, and
        // toMessageEntity() has no way to know a link-preview image was already downloaded by a
        // previous call unless it's told, so a full resync (every reconnect) doesn't wipe it back
        // to null and force a redundant re-download every time.
        val existingLinkPreviewImagePath = messageDao.linkPreviewImagePath(dto.id)
        messageDao.upsert(dto.toMessageEntity(existingLinkPreviewImagePath))

        reactionDao.deleteAllForMessage(dto.id)
        dto.reactions.forEach { reaction ->
            reactionDao.upsert(
                ReactionEntity(
                    messageId = dto.id,
                    partnerId = reaction.userId,
                    emoji = reaction.emoji,
                    reactedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }

        replacePollOptions(dto)

        val mediaId = dto.mediaId
        if (mediaId != null && dto.deletedAt == null) {
            when (dto.type) {
                MessageTypeDto.IMAGE -> {
                    if (!attachmentDao.existsForMessage(dto.id)) {
                        val downloaded = runCatching { mediaStorage.downloadToMediaDir(mediaId, isVoice = false) }.getOrNull()
                        if (downloaded != null) {
                            attachmentDao.insert(
                                AttachmentEntity(
                                    id = UUID.randomUUID().toString(),
                                    messageId = dto.id,
                                    attachmentKind = "IMAGE",
                                    filePath = downloaded.filePath,
                                    mimeType = downloaded.mimeType,
                                    widthPx = downloaded.widthPx,
                                    heightPx = downloaded.heightPx,
                                    sizeBytes = File(downloaded.filePath).length(),
                                ),
                            )
                        }
                    }
                }
                MessageTypeDto.VOICE -> {
                    if (!voiceNoteDao.existsForMessage(dto.id)) {
                        val downloaded = runCatching { mediaStorage.downloadToMediaDir(mediaId, isVoice = true) }.getOrNull()
                        if (downloaded != null) {
                            voiceNoteDao.insert(
                                VoiceNoteEntity(
                                    id = UUID.randomUUID().toString(),
                                    messageId = dto.id,
                                    filePath = downloaded.filePath,
                                    durationMillis = (downloaded.durationMs ?: 0).toLong(),
                                ),
                            )
                        }
                    }
                }
                // Deliberately does NOT download the video itself here — unlike image/voice,
                // which are small enough to always fetch eagerly, a video can be up to 150MB.
                // Only its poster-frame thumbnail (a plain message_image asset) downloads now;
                // the real bytes are fetched lazily later via MessageRepository.downloadVideoIfNeeded,
                // on tap, from the video viewer.
                MessageTypeDto.VIDEO -> {
                    if (!attachmentDao.existsForMessage(dto.id)) {
                        val videoAsset = runCatching { mediaStorage.getAssetMetadata(mediaId) }.getOrNull()
                        val thumbnailId = videoAsset?.thumbnailMediaId
                        val downloadedThumbnail = thumbnailId?.let {
                            runCatching { mediaStorage.downloadToMediaDir(it, isVoice = false) }.getOrNull()
                        }
                        if (videoAsset != null && downloadedThumbnail != null) {
                            attachmentDao.insert(
                                AttachmentEntity(
                                    id = UUID.randomUUID().toString(),
                                    messageId = dto.id,
                                    attachmentKind = "VIDEO",
                                    filePath = downloadedThumbnail.filePath,
                                    mimeType = videoAsset.mimeType,
                                    widthPx = downloadedThumbnail.widthPx,
                                    heightPx = downloadedThumbnail.heightPx,
                                    sizeBytes = videoAsset.sizeBytes,
                                    mediaId = videoAsset.id,
                                    durationMs = videoAsset.durationMs,
                                    localVideoFilePath = null,
                                ),
                            )
                        }
                    }
                }
                // No preview to fetch at all — just the metadata needed to render the file-icon
                // bubble (filename/size). The actual bytes stay lazy, same reasoning as VIDEO.
                MessageTypeDto.DOCUMENT -> {
                    if (!attachmentDao.existsForMessage(dto.id)) {
                        val documentAsset = runCatching { mediaStorage.getAssetMetadata(mediaId) }.getOrNull()
                        if (documentAsset != null) {
                            attachmentDao.insert(
                                AttachmentEntity(
                                    id = UUID.randomUUID().toString(),
                                    messageId = dto.id,
                                    attachmentKind = "DOCUMENT",
                                    filePath = "",
                                    mimeType = documentAsset.mimeType,
                                    widthPx = null,
                                    heightPx = null,
                                    sizeBytes = documentAsset.sizeBytes,
                                    mediaId = documentAsset.id,
                                    originalFilename = documentAsset.originalFilename,
                                    localDocumentFilePath = null,
                                ),
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        val linkPreviewMediaId = dto.linkPreviewMediaId
        val linkPreviewUrl = dto.linkPreviewUrl
        if (linkPreviewMediaId != null && linkPreviewUrl != null && existingLinkPreviewImagePath == null) {
            val downloaded = runCatching { mediaStorage.downloadToMediaDir(linkPreviewMediaId, isVoice = false) }.getOrNull()
            if (downloaded != null) {
                messageDao.setLinkPreview(
                    id = dto.id,
                    url = linkPreviewUrl,
                    title = dto.linkPreviewTitle,
                    description = dto.linkPreviewDescription,
                    imagePath = downloaded.filePath,
                )
            }
        }
    }

    /** Shared by [applyRemoteMessage] (sync/WS) and [sendPoll] (this device's own send) — the
     * one place a [MessageOutDto]'s poll option/vote state gets written to Room, so both paths
     * apply the exact same delete-then-insert-wholesale logic rather than drifting apart. */
    private suspend fun replacePollOptions(dto: MessageOutDto) {
        pollOptionDao.deleteForMessage(dto.id)
        val options = dto.poll?.options ?: return
        pollOptionDao.insertAll(
            options.map { option ->
                PollOptionEntity(
                    id = option.id,
                    messageId = dto.id,
                    optionText = option.text,
                    orderIndex = option.orderIndex,
                    votedByPartnerIds = option.votedBy,
                )
            },
        )
    }

    private fun MessageOutDto.toMessageEntity(linkPreviewImagePath: String? = null) = MessageEntity(
        id = id,
        senderId = senderId,
        body = body,
        replyToId = replyToId,
        sentAtEpochMillis = createdAt.isoDateTimeToEpochMillis(),
        editedAtEpochMillis = editedAt?.isoDateTimeToEpochMillis(),
        isDeleted = deletedAt != null,
        isStarred = isStarredByMe,
        isPinned = isPinned,
        isRead = readAt != null,
        deliveryState = if (deliveredAt != null) MessageDeliveryState.DELIVERED else MessageDeliveryState.SENT,
        linkPreviewUrl = linkPreviewUrl,
        linkPreviewTitle = linkPreviewTitle,
        linkPreviewDescription = linkPreviewDescription,
        linkPreviewImagePath = linkPreviewImagePath,
        readAtEpochMillis = readAt?.isoDateTimeToEpochMillis(),
        deliveredAtEpochMillis = deliveredAt?.isoDateTimeToEpochMillis(),
        pollAllowsMultiple = poll?.allowsMultiple ?: false,
        pollClosedAtEpochMillis = poll?.closedAt?.isoDateTimeToEpochMillis(),
    )

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_SYNC_PAGES = 5
    }
}

/** The `link_preview` object inside a `message.link_preview_ready` WS event — narrower than
 * [MessageOutDto], since that event only ever carries these 4 fields (see
 * `app/services/message_service.py`'s `_fetch_and_attach_link_preview`). */
@Serializable
private data class LinkPreviewWsDto(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
)

internal fun MessageWithDetails.toConsoleMessage(selfPartnerId: String?, hiddenIds: Set<String> = emptySet()): ConsoleMessage {
    val repliedTo = repliedToMessages.firstOrNull()
    val attachment = attachments.firstOrNull()
    val voiceNote = voiceNotes.firstOrNull()
    return ConsoleMessage(
        id = message.id,
        senderId = message.senderId,
        isFromSelf = message.senderId == selfPartnerId,
        body = message.body,
        sentAtEpochMillis = message.sentAtEpochMillis,
        editedAtEpochMillis = message.editedAtEpochMillis,
        // "Delete for me" (hiddenIds) renders identically to a real (sender-initiated) delete —
        // see HomeScreen.kt's tombstone rendering, which only checks this one flag — even though
        // the real body/attachment/voiceNote below stay populated; the Compose layer is what
        // actually suppresses them.
        isDeleted = message.isDeleted || message.id in hiddenIds,
        isStarred = message.isStarred,
        isPinned = message.isPinned,
        isRead = message.isRead,
        replyPreview = repliedTo?.let {
            ReplyPreview(
                messageId = it.id,
                senderId = it.senderId,
                bodyPreview = it.body,
                isDeleted = it.isDeleted || it.id in hiddenIds,
                hasMedia = repliedToAttachments.isNotEmpty() || repliedToVoiceNotes.isNotEmpty(),
            )
        },
        attachment = attachment?.let {
            MessageAttachment(
                kind = when (it.attachmentKind) {
                    "VIDEO" -> MessageAttachmentKind.VIDEO
                    "DOCUMENT" -> MessageAttachmentKind.DOCUMENT
                    else -> MessageAttachmentKind.IMAGE
                },
                filePath = it.filePath,
                mimeType = it.mimeType,
                widthPx = it.widthPx,
                heightPx = it.heightPx,
                sizeBytes = it.sizeBytes,
                mediaId = it.mediaId,
                durationMillis = it.durationMs?.toLong(),
                localVideoFilePath = it.localVideoFilePath,
                originalFilename = it.originalFilename,
                localDocumentFilePath = it.localDocumentFilePath,
            )
        },
        voiceNote = voiceNote?.let { MessageVoiceNote(filePath = it.filePath, durationMillis = it.durationMillis) },
        linkPreview = message.linkPreviewUrl?.let {
            MessageLinkPreview(
                url = it,
                title = message.linkPreviewTitle,
                description = message.linkPreviewDescription,
                imagePath = message.linkPreviewImagePath,
            )
        },
        reactions = reactions.map {
            MessageReaction(partnerId = it.partnerId, emoji = it.emoji, isMine = it.partnerId == selfPartnerId)
        },
        myReaction = reactions.firstOrNull { it.partnerId == selfPartnerId }?.emoji,
        poll = pollOptions.takeIf { it.isNotEmpty() }?.let { options ->
            MessagePoll(
                allowsMultiple = message.pollAllowsMultiple,
                closedAtEpochMillis = message.pollClosedAtEpochMillis,
                options = options.sortedBy { it.orderIndex }.map { option ->
                    MessagePollOption(
                        id = option.id,
                        text = option.optionText,
                        orderIndex = option.orderIndex,
                        votedByPartnerIds = option.votedByPartnerIds,
                        votedBySelf = selfPartnerId != null && selfPartnerId in option.votedByPartnerIds,
                    )
                },
            )
        },
        deliveryState = message.deliveryState,
        readAtEpochMillis = message.readAtEpochMillis,
        deliveredAtEpochMillis = message.deliveredAtEpochMillis,
    )
}
