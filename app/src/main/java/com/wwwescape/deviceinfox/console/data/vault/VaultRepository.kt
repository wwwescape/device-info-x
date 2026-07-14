package com.wwwescape.deviceinfox.console.data.vault

import android.net.Uri
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.db.VaultAlbumDao
import com.wwwescape.deviceinfox.console.data.db.VaultAlbumEntity
import com.wwwescape.deviceinfox.console.data.db.VaultItemDao
import com.wwwescape.deviceinfox.console.data.db.VaultItemEntity
import com.wwwescape.deviceinfox.console.data.messaging.MediaStorage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.LockerApi
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.MediaCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import java.io.File
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real, server-backed Safe Locker (Phase 11.8) — `Room is what the UI sees` still holds:
 * [items]/[albums] are the same Room-backed `Flow`s `VaultViewModel` has always observed.
 *
 * The server's Safe Locker is shared-but-owner-edits-only (`locker_service.py`: both partners
 * see every item/album whose `owner_id` is either of them, but only the creator can rename/
 * edit/delete their own) — a real design shift from Phase 10's local-only implementation, which
 * had no ownership concept at all. [VaultItem.isMine]/[VaultAlbum.isMine] (derived here by
 * comparing the synced `ownerId` against [PartnerRepository.selfProfile]'s id) is what
 * `VaultScreen` gates its edit affordances on, the same pattern as the Period Tracker's
 * `canEdit`.
 *
 * Both albums and items push over the WebSocket (`locker_service.py` calls
 * `notification_service.notify_user` on every mutation) — deltas are applied directly, same as
 * `MessageRepository`/`CalendarRepository`. [refresh] still runs once eagerly on construction as
 * a catch-up fetch, since WS delivery only reaches a live connection.
 */
@Singleton
class VaultRepository @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    private val vaultAlbumDao: VaultAlbumDao,
    private val vaultStorage: VaultStorage,
    private val mediaStorage: MediaStorage,
    private val partnerRepository: PartnerRepository,
    private val lockerApi: LockerApi,
    private val webSocketClient: ConsoleWebSocketClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lockerWipedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires only when the *partner's* device wipes their Safe Locker data (Settings → Safe
     * Locker → Delete data, on their end) — this device's own screen already updates reactively
     * via Room. */
    val lockerWipedEvent: SharedFlow<Unit> = _lockerWipedEvent.asSharedFlow()

    init {
        scope.launch { runCatching { refresh() } }
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleWsEvent(event) } }
        }
    }

    val items: Flow<List<VaultItem>> = combine(vaultItemDao.observeAll(), partnerRepository.selfProfile) { entities, self ->
        entities.map { it.toVaultItem(self?.id) }
    }
    val albums: Flow<List<VaultAlbum>> = combine(vaultAlbumDao.observeAll(), partnerRepository.selfProfile) { entities, self ->
        entities.map { it.toVaultAlbum(self?.id) }
    }

    /** Full sync: fetches everything visible (self + partner), applies each row the same way a
     * WS delta would, then prunes any local row no longer present remotely — covers a delete
     * that happened while this device's console was closed, which WS alone can't (it only
     * reaches a live connection). */
    suspend fun refresh() {
        val remoteAlbums = consoleApiCall { lockerApi.listAlbums() }
        remoteAlbums.forEach { applyRemoteAlbum(it) }
        val remoteAlbumIds = remoteAlbums.map { it.id }.toSet()
        vaultAlbumDao.getAllSnapshot().filter { it.id !in remoteAlbumIds }.forEach { vaultAlbumDao.delete(it.id) }

        val remoteItems = consoleApiCall { lockerApi.listItems() }
        remoteItems.forEach { applyRemoteItem(it) }
        val remoteItemIds = remoteItems.map { it.id }.toSet()
        vaultItemDao.getAllSnapshot().filter { it.id !in remoteItemIds }.forEach { stale ->
            vaultItemDao.delete(stale.id)
            sweepItemFiles(stale)
        }
    }

    /** [VaultItemEntity.filePath] is blank for a document row (nothing to delete there) and a
     * video/document may separately have a lazily-downloaded [VaultItemEntity.localVideoFilePath]/
     * [VaultItemEntity.localDocumentFilePath] on top of it — same sweep shape
     * `MessageRepository`/`CalendarRepository` already use. */
    private suspend fun sweepItemFiles(entity: VaultItemEntity) {
        listOfNotNull(entity.filePath.takeIf { it.isNotBlank() }, entity.localVideoFilePath, entity.localDocumentFilePath)
            .forEach { vaultStorage.deleteFile(it) }
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "locker.album.created", "locker.album.updated" -> {
                val dto = event.data["album"]?.let { json.decodeFromJsonElement<LockerAlbumOutDto>(it) } ?: return
                applyRemoteAlbum(dto)
            }
            "locker.album.deleted" -> {
                val id = event.data.stringField("album_id") ?: return
                vaultAlbumDao.delete(id)
            }
            "locker.item.created", "locker.item.updated" -> {
                val dto = event.data["item"]?.let { json.decodeFromJsonElement<LockerItemOutDto>(it) } ?: return
                applyRemoteItem(dto)
            }
            "locker.item.deleted" -> {
                val id = event.data.stringField("item_id") ?: return
                val stale = vaultItemDao.getAllSnapshot().firstOrNull { it.id == id } ?: return
                vaultItemDao.delete(id)
                sweepItemFiles(stale)
            }
            "locker.bulk_deleted" -> {
                refresh()
                _lockerWipedEvent.tryEmit(Unit)
            }
        }
    }

    private fun JsonObject.stringField(key: String): String? = this[key]?.jsonPrimitive?.content

    private suspend fun applyRemoteAlbum(dto: LockerAlbumOutDto) {
        vaultAlbumDao.upsert(
            VaultAlbumEntity(id = dto.id, name = dto.name, createdAtEpochMillis = dto.createdAt.isoDateTimeToEpochMillis(), ownerId = dto.ownerId),
        )
    }

    /** Only downloads the underlying file for a locker item this device has never seen before —
     * an already-known id (self's own upload, or a partner's item seen on an earlier sync)
     * only has its mutable metadata refreshed, never its immutable [VaultItemEntity.filePath]/
     * [VaultItemEntity.mimeType]. Dispatches per category, mirroring
     * `MessageRepository.applyRemoteMessage`'s per-kind branches exactly: [LockerCategoryDto.PHOTO]
     * downloads eagerly (as always), [LockerCategoryDto.VIDEO] eagerly downloads only its
     * poster-frame thumbnail (leaving the full video lazy — it can be large),
     * [LockerCategoryDto.DOCUMENT] stores metadata only (fully lazy). [LockerCategoryDto.NOTE]/
     * [LockerCategoryDto.OTHER] stay unsupported and are skipped, out of scope for this feature. */
    private suspend fun applyRemoteItem(dto: LockerItemOutDto) {
        if (vaultItemDao.exists(dto.id)) {
            vaultItemDao.updateMetadata(dto.id, dto.albumId, dto.isFavorite, dto.description, dto.ownerId)
            return
        }
        val mediaId = dto.mediaId ?: return
        when (dto.category) {
            LockerCategoryDto.PHOTO -> {
                val downloaded = runCatching { vaultStorage.downloadToVaultDir(mediaId) }.getOrNull() ?: return
                vaultItemDao.upsert(
                    VaultItemEntity(
                        id = dto.id,
                        albumId = dto.albumId,
                        filePath = downloaded.filePath,
                        mimeType = downloaded.mimeType,
                        isFavorite = dto.isFavorite,
                        addedAtEpochMillis = dto.createdAt.isoDateTimeToEpochMillis(),
                        originalMessageId = null,
                        caption = dto.description,
                        ownerId = dto.ownerId,
                        attachmentKind = "IMAGE",
                    ),
                )
            }
            LockerCategoryDto.VIDEO -> {
                val asset = runCatching { mediaStorage.getAssetMetadata(mediaId) }.getOrNull() ?: return
                val thumbnailId = asset.thumbnailMediaId ?: return
                val downloadedThumbnail = runCatching { mediaStorage.downloadToMediaDir(thumbnailId, isVoice = false) }.getOrNull() ?: return
                vaultItemDao.upsert(
                    VaultItemEntity(
                        id = dto.id,
                        albumId = dto.albumId,
                        filePath = downloadedThumbnail.filePath,
                        mimeType = asset.mimeType,
                        isFavorite = dto.isFavorite,
                        addedAtEpochMillis = dto.createdAt.isoDateTimeToEpochMillis(),
                        originalMessageId = null,
                        caption = dto.description,
                        ownerId = dto.ownerId,
                        attachmentKind = "VIDEO",
                        mediaId = asset.id,
                        durationMs = asset.durationMs,
                        localVideoFilePath = null,
                        sizeBytes = asset.sizeBytes,
                    ),
                )
            }
            LockerCategoryDto.DOCUMENT -> {
                val asset = runCatching { mediaStorage.getAssetMetadata(mediaId) }.getOrNull() ?: return
                vaultItemDao.upsert(
                    VaultItemEntity(
                        id = dto.id,
                        albumId = dto.albumId,
                        filePath = "",
                        mimeType = asset.mimeType,
                        isFavorite = dto.isFavorite,
                        addedAtEpochMillis = dto.createdAt.isoDateTimeToEpochMillis(),
                        originalMessageId = null,
                        caption = dto.description,
                        ownerId = dto.ownerId,
                        attachmentKind = "DOCUMENT",
                        mediaId = asset.id,
                        originalFilename = asset.originalFilename,
                        localDocumentFilePath = null,
                        sizeBytes = asset.sizeBytes,
                    ),
                )
            }
            LockerCategoryDto.NOTE, LockerCategoryDto.OTHER -> return
        }
    }

    suspend fun importImages(sourceUris: List<Uri>, albumId: String?) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        sourceUris.forEach { uri ->
            val imported = vaultStorage.importImage(uri)
            val asset = vaultStorage.uploadToServer(imported.filePath, imported.mimeType)
            val result = consoleApiCall {
                lockerApi.createItem(
                    LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = asset.id, albumId = albumId, category = LockerCategoryDto.PHOTO),
                )
            }
            vaultItemDao.upsert(
                VaultItemEntity(
                    id = result.id,
                    albumId = result.albumId,
                    filePath = imported.filePath,
                    mimeType = imported.mimeType,
                    isFavorite = result.isFavorite,
                    addedAtEpochMillis = result.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = null,
                    caption = result.description,
                    ownerId = result.ownerId.ifBlank { selfId },
                    attachmentKind = "IMAGE",
                ),
            )
        }
    }

    /** Mirrors [importImages]'s per-file loop shape, but each video needs [MediaStorage]'s
     * copy-probe-thumbnail-then-video sequence ([MessageRepository.sendVideo]'s exact shape)
     * instead of a single copy+upload — the poster thumbnail is uploaded first so its asset id
     * can be attached to the video asset's own upload. Every locker upload shares the single
     * `MediaCategory.LOCKER_FILE` at the media-asset level regardless of kind — only the
     * *locker item*'s own `category` (`LockerCategoryDto.VIDEO`) distinguishes it. */
    suspend fun importVideos(sourceUris: List<Uri>, albumId: String?) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        sourceUris.forEach { uri ->
            val copied = mediaStorage.copyVideoToPrivateStorage(uri)
            val probe = mediaStorage.probeVideoDurationAndThumbnail(copied.filePath)
            val thumbnailAsset = probe.thumbnailFilePath?.let { thumbPath ->
                mediaStorage.uploadToServer(thumbPath, "image/jpeg", MediaCategoryDto.LOCKER_FILE)
            }
            val videoAsset = mediaStorage.uploadToServer(
                copied.filePath,
                copied.mimeType,
                MediaCategoryDto.LOCKER_FILE,
                durationMs = probe.durationMs,
                thumbnailMediaId = thumbnailAsset?.id,
            )
            val result = consoleApiCall {
                lockerApi.createItem(
                    LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = videoAsset.id, albumId = albumId, category = LockerCategoryDto.VIDEO),
                )
            }
            vaultItemDao.upsert(
                VaultItemEntity(
                    id = result.id,
                    albumId = result.albumId,
                    filePath = probe.thumbnailFilePath ?: copied.filePath,
                    mimeType = copied.mimeType,
                    isFavorite = result.isFavorite,
                    addedAtEpochMillis = result.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = null,
                    caption = result.description,
                    ownerId = result.ownerId.ifBlank { selfId },
                    attachmentKind = "VIDEO",
                    mediaId = videoAsset.id,
                    durationMs = probe.durationMs,
                    // The sender already has the file — no reason to make them re-download their
                    // own import, same reasoning as sendVideo's localVideoFilePath.
                    localVideoFilePath = copied.filePath,
                    sizeBytes = copied.sizeBytes,
                ),
            )
        }
    }

    /** Mirrors [importVideos]'s shape but simpler — a document has no thumbnail to generate. */
    suspend fun importDocuments(sourceUris: List<Uri>, albumId: String?) {
        val selfId = partnerRepository.ensureSelfProfileExists()
        sourceUris.forEach { uri ->
            val copied = mediaStorage.copyDocumentToPrivateStorage(uri)
            val asset = mediaStorage.uploadToServer(
                copied.filePath,
                copied.mimeType,
                MediaCategoryDto.LOCKER_FILE,
                originalFilename = copied.originalFilename,
            )
            val result = consoleApiCall {
                lockerApi.createItem(
                    LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = asset.id, albumId = albumId, category = LockerCategoryDto.DOCUMENT),
                )
            }
            vaultItemDao.upsert(
                VaultItemEntity(
                    id = result.id,
                    albumId = result.albumId,
                    filePath = "",
                    mimeType = copied.mimeType,
                    isFavorite = result.isFavorite,
                    addedAtEpochMillis = result.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = null,
                    caption = result.description,
                    ownerId = result.ownerId.ifBlank { selfId },
                    attachmentKind = "DOCUMENT",
                    mediaId = asset.id,
                    originalFilename = copied.originalFilename,
                    localDocumentFilePath = copied.filePath,
                    sizeBytes = copied.sizeBytes,
                ),
            )
        }
    }

    /** No-ops if the video's bytes are already local (this device's own import, or already
     * played once this session) — otherwise fetches them lazily via
     * [MediaStorage.downloadVideoToMediaDir], mirroring `MessageRepository.downloadVideoIfNeeded`. */
    suspend fun downloadVideoIfNeeded(itemId: String, item: VaultItem): String {
        item.localVideoFilePath?.let { return it }
        val mediaId = requireNotNull(item.mediaId) { "video item missing mediaId" }
        val downloaded = mediaStorage.downloadVideoToMediaDir(mediaId, item.mimeType)
        vaultItemDao.updateLocalVideoPath(itemId, downloaded.filePath)
        return downloaded.filePath
    }

    /** Mirrors `MessageRepository.downloadDocumentIfNeeded`. */
    suspend fun downloadDocumentIfNeeded(itemId: String, item: VaultItem): String {
        item.localDocumentFilePath?.let { return it }
        val mediaId = requireNotNull(item.mediaId) { "document item missing mediaId" }
        val downloaded = mediaStorage.downloadDocumentToMediaDir(mediaId, item.mimeType)
        vaultItemDao.updateLocalDocumentPath(itemId, downloaded.filePath)
        return downloaded.filePath
    }

    /** The manual "Save to Vault" action on a chat message's or calendar event's attachment
     * (`HomeScreen`'s/`EventDetailScreen`'s attachment previews) — same create-item flow as
     * [importImages]/[importVideos]/[importDocuments], but importing from an already-local
     * attachment (see [VaultStorage.importFromFile]) instead of a freshly-picked content [Uri].
     * Exactly one of [originalMessageId]/[originalEventId] is expected non-null per call — see
     * [VaultItemEntity.originalMessageId]/[VaultItemEntity.originalAttachmentMediaId]'s own doc
     * comments for why a message only needs the former (at most one attachment per message) while
     * an event needs both (several attachments per event are possible). For [MessageAttachmentKind.VIDEO]/
     * [MessageAttachmentKind.DOCUMENT], [attachment] must already have its lazy local path
     * resolved (the caller ensures this first — mirrors `VaultViewModel.exportItem`'s own
     * "ensure downloaded, then act on a copy of the attachment with that path filled in" shape). */
    suspend fun saveAttachmentToVault(
        attachment: MessageAttachment,
        originalMessageId: String? = null,
        originalEventId: String? = null,
    ): Boolean {
        val alreadySaved = when {
            originalMessageId != null -> vaultItemDao.existsForMessage(originalMessageId)
            originalEventId != null && attachment.mediaId != null ->
                vaultItemDao.existsForEventAttachment(originalEventId, attachment.mediaId)
            else -> false
        }
        if (alreadySaved) return false

        val selfId = partnerRepository.ensureSelfProfileExists()
        val result = when (attachment.kind) {
            MessageAttachmentKind.IMAGE -> {
                val imported = vaultStorage.importFromFile(attachment.filePath, attachment.mimeType)
                val asset = vaultStorage.uploadToServer(imported.filePath, imported.mimeType)
                val item = consoleApiCall {
                    lockerApi.createItem(
                        LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = asset.id, albumId = null, category = LockerCategoryDto.PHOTO),
                    )
                }
                VaultItemEntity(
                    id = item.id,
                    albumId = item.albumId,
                    filePath = imported.filePath,
                    mimeType = imported.mimeType,
                    isFavorite = item.isFavorite,
                    addedAtEpochMillis = item.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = originalMessageId,
                    originalEventId = originalEventId,
                    originalAttachmentMediaId = attachment.mediaId,
                    caption = item.description,
                    ownerId = item.ownerId.ifBlank { selfId },
                    attachmentKind = "IMAGE",
                )
            }
            MessageAttachmentKind.VIDEO -> {
                val videoLocalPath = requireNotNull(attachment.localVideoFilePath) { "video must be downloaded before saving to vault" }
                val importedThumb = vaultStorage.importFromFile(attachment.filePath, "image/jpeg")
                val importedVideo = vaultStorage.importFromFile(videoLocalPath, attachment.mimeType)
                val thumbnailAsset = mediaStorage.uploadToServer(importedThumb.filePath, importedThumb.mimeType, MediaCategoryDto.LOCKER_FILE)
                val videoAsset = mediaStorage.uploadToServer(
                    importedVideo.filePath,
                    importedVideo.mimeType,
                    MediaCategoryDto.LOCKER_FILE,
                    durationMs = attachment.durationMillis?.toInt(),
                    thumbnailMediaId = thumbnailAsset.id,
                )
                val item = consoleApiCall {
                    lockerApi.createItem(
                        LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = videoAsset.id, albumId = null, category = LockerCategoryDto.VIDEO),
                    )
                }
                VaultItemEntity(
                    id = item.id,
                    albumId = item.albumId,
                    filePath = importedThumb.filePath,
                    mimeType = importedVideo.mimeType,
                    isFavorite = item.isFavorite,
                    addedAtEpochMillis = item.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = originalMessageId,
                    originalEventId = originalEventId,
                    originalAttachmentMediaId = attachment.mediaId,
                    caption = item.description,
                    ownerId = item.ownerId.ifBlank { selfId },
                    attachmentKind = "VIDEO",
                    mediaId = videoAsset.id,
                    durationMs = attachment.durationMillis?.toInt(),
                    localVideoFilePath = importedVideo.filePath,
                    sizeBytes = File(importedVideo.filePath).length(),
                )
            }
            MessageAttachmentKind.DOCUMENT -> {
                val documentLocalPath = requireNotNull(attachment.localDocumentFilePath) { "document must be downloaded before saving to vault" }
                val imported = vaultStorage.importFromFile(documentLocalPath, attachment.mimeType)
                val asset = mediaStorage.uploadToServer(
                    imported.filePath,
                    imported.mimeType,
                    MediaCategoryDto.LOCKER_FILE,
                    originalFilename = attachment.originalFilename,
                )
                val item = consoleApiCall {
                    lockerApi.createItem(
                        LockerItemCreateDto(title = DEFAULT_TITLE, mediaId = asset.id, albumId = null, category = LockerCategoryDto.DOCUMENT),
                    )
                }
                VaultItemEntity(
                    id = item.id,
                    albumId = item.albumId,
                    filePath = "",
                    mimeType = imported.mimeType,
                    isFavorite = item.isFavorite,
                    addedAtEpochMillis = item.createdAt.isoDateTimeToEpochMillis(),
                    originalMessageId = originalMessageId,
                    originalEventId = originalEventId,
                    originalAttachmentMediaId = attachment.mediaId,
                    caption = item.description,
                    ownerId = item.ownerId.ifBlank { selfId },
                    attachmentKind = "DOCUMENT",
                    mediaId = asset.id,
                    originalFilename = attachment.originalFilename,
                    localDocumentFilePath = imported.filePath,
                    sizeBytes = File(imported.filePath).length(),
                )
            }
        }
        vaultItemDao.upsert(result)
        return true
    }

    suspend fun deleteItem(item: VaultItem) {
        consoleApiCall { lockerApi.deleteItem(item.id) }
        vaultItemDao.delete(item.id)
        listOfNotNull(item.filePath.takeIf { it.isNotBlank() }, item.localVideoFilePath, item.localDocumentFilePath)
            .forEach { vaultStorage.deleteFile(it) }
    }

    suspend fun setFavorite(item: VaultItem, favorite: Boolean) {
        val result = consoleApiCall {
            lockerApi.updateItem(item.id, item.toUpdateDto(isFavorite = favorite))
        }
        vaultItemDao.updateMetadata(item.id, result.albumId, result.isFavorite, result.description, result.ownerId)
    }

    suspend fun setAlbum(item: VaultItem, albumId: String?) {
        val result = consoleApiCall {
            lockerApi.updateItem(item.id, item.toUpdateDto(albumId = albumId))
        }
        vaultItemDao.updateMetadata(item.id, result.albumId, result.isFavorite, result.description, result.ownerId)
    }

    suspend fun setCaption(item: VaultItem, caption: String?) {
        val result = consoleApiCall {
            lockerApi.updateItem(item.id, item.toUpdateDto(caption = caption))
        }
        vaultItemDao.updateMetadata(item.id, result.albumId, result.isFavorite, result.description, result.ownerId)
    }

    /** Every field is resent with its current value except whichever one actually changed — see
     * `LockerItemUpdateDto`'s doc comment for why this "full replace" shape is necessary. */
    private fun VaultItem.toUpdateDto(
        albumId: String? = this.albumId,
        isFavorite: Boolean = this.isFavorite,
        caption: String? = this.caption,
    ) = LockerItemUpdateDto(
        title = caption?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE,
        description = caption,
        albumId = albumId,
        isFavorite = isFavorite,
    )

    /** [item] must already have its lazy path resolved for video/document (the caller —
     * `VaultViewModel.exportItem` — ensures this first via [downloadVideoIfNeeded]/
     * [downloadDocumentIfNeeded] before calling here). */
    suspend fun exportItem(item: VaultItem, destinationUri: Uri) {
        val sourcePath = when (item.kind) {
            MessageAttachmentKind.VIDEO -> requireNotNull(item.localVideoFilePath) { "video not downloaded yet" }
            MessageAttachmentKind.DOCUMENT -> requireNotNull(item.localDocumentFilePath) { "document not downloaded yet" }
            MessageAttachmentKind.IMAGE -> item.filePath
        }
        vaultStorage.exportToDocument(sourcePath, destinationUri)
    }

    suspend fun createAlbum(name: String): String {
        val result = consoleApiCall { lockerApi.createAlbum(LockerAlbumCreateDto(name)) }
        vaultAlbumDao.upsert(
            VaultAlbumEntity(id = result.id, name = result.name, createdAtEpochMillis = result.createdAt.isoDateTimeToEpochMillis(), ownerId = result.ownerId),
        )
        return result.id
    }

    suspend fun renameAlbum(id: String, name: String) {
        val result = consoleApiCall { lockerApi.renameAlbum(id, LockerAlbumUpdateDto(name)) }
        vaultAlbumDao.rename(id, result.name)
    }

    suspend fun deleteAlbum(id: String) {
        consoleApiCall { lockerApi.deleteAlbum(id) }
        vaultAlbumDao.delete(id)
    }

    /** The "Delete data" action for Safe Locker in Settings — owner-scoped, matches
     * `locker_service.delete_all_own_data` (real hard delete + on-disk file cleanup server-side,
     * also clears the caller's albums). [refresh] already does exactly the local cleanup needed
     * post-wipe: fetch what's left (the partner's own items/albums, untouched), prune anything
     * local that's no longer remote — including deleting each pruned item's on-disk file. */
    suspend fun deleteAllMyLockerData() {
        consoleApiCall { lockerApi.deleteAllMine() }
        refresh()
    }

    private fun VaultItemEntity.toVaultItem(selfId: String?) = VaultItem(
        id = id,
        albumId = albumId,
        filePath = filePath,
        mimeType = mimeType,
        isFavorite = isFavorite,
        addedAtEpochMillis = addedAtEpochMillis,
        caption = caption,
        isMine = ownerId == null || ownerId == selfId,
        kind = when (attachmentKind) {
            "VIDEO" -> MessageAttachmentKind.VIDEO
            "DOCUMENT" -> MessageAttachmentKind.DOCUMENT
            else -> MessageAttachmentKind.IMAGE
        },
        mediaId = mediaId,
        durationMillis = durationMs?.toLong(),
        localVideoFilePath = localVideoFilePath,
        originalFilename = originalFilename,
        localDocumentFilePath = localDocumentFilePath,
        sizeBytes = sizeBytes,
    )

    private fun VaultAlbumEntity.toVaultAlbum(selfId: String?) = VaultAlbum(
        id = id,
        name = name,
        createdAtEpochMillis = createdAtEpochMillis,
        isMine = ownerId == null || ownerId == selfId,
    )

    private companion object {
        /** The server requires a non-blank `title`; the console has no title field of its own
         * (only the optional caption), so this is what's sent whenever there's no caption to
         * derive one from. Never shown anywhere in the UI. */
        const val DEFAULT_TITLE = "Photo"
    }
}
