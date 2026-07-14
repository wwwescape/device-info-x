package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY addedAtEpochMillis DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Upsert
    suspend fun upsert(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE vault_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE vault_items SET albumId = :albumId WHERE id = :id")
    suspend fun setAlbum(id: String, albumId: String?)

    @Query("UPDATE vault_items SET caption = :caption WHERE id = :id")
    suspend fun setCaption(id: String, caption: String?)

    /** Sync (Phase 11.8) checks this before downloading a partner's item's file — the file
     * content is immutable once a locker item exists (only metadata like caption/album/favorite
     * changes), so a re-sync must never re-download something already cached locally, same
     * reasoning as `AttachmentDao.existsForMessage`. */
    @Query("SELECT EXISTS(SELECT 1 FROM vault_items WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /** Backs the "Save to Vault" chat-attachment action's dedupe check — a message's attachment
     * should only ever produce one locker item, no matter how many times the action is tapped. */
    @Query("SELECT EXISTS(SELECT 1 FROM vault_items WHERE originalMessageId = :messageId)")
    suspend fun existsForMessage(messageId: String): Boolean

    /** Mirrors [existsForMessage]'s dedupe role, but scoped by [VaultItemEntity.originalAttachmentMediaId]
     * too, not just [eventId] — unlike a message (at most one attachment), a calendar event can
     * have several, so "this event already has a saved item" isn't the same question as "this
     * specific attachment was already saved"; only the latter is the correct dedupe check here.
     * Calendar attachments always carry a `mediaId` (unlike Messages, where it's video/document-only
     * — see `CalendarAttachmentEntity`'s own doc comment for why), so this is always resolvable. */
    @Query("SELECT EXISTS(SELECT 1 FROM vault_items WHERE originalEventId = :eventId AND originalAttachmentMediaId = :attachmentMediaId)")
    suspend fun existsForEventAttachment(eventId: String, attachmentMediaId: String): Boolean

    /** Updates only what a remote metadata change (WS event or re-sync) can actually carry —
     * [filePath]/[mimeType] are never touched, since the server has no concept of them and the
     * underlying file is immutable once uploaded. */
    @Query("UPDATE vault_items SET albumId = :albumId, isFavorite = :isFavorite, caption = :caption, ownerId = :ownerId WHERE id = :id")
    suspend fun updateMetadata(id: String, albumId: String?, isFavorite: Boolean, caption: String?, ownerId: String?)

    /** One-shot snapshot (not a `Flow`) — [com.wwwescape.deviceinfox.console.data.vault.VaultRepository.refresh]
     * uses this to both diff against the server's response (which rows to prune) and recover
     * [VaultItemEntity.filePath] for a pruned row's underlying file before deleting it. */
    @Query("SELECT * FROM vault_items")
    suspend fun getAllSnapshot(): List<VaultItemEntity>

    /** Mirrors [AttachmentDao.updateLocalVideoPath] — called once a video item's lazily-fetched
     * bytes land locally. */
    @Query("UPDATE vault_items SET localVideoFilePath = :path WHERE id = :id")
    suspend fun updateLocalVideoPath(id: String, path: String)

    /** Mirrors [AttachmentDao.updateLocalDocumentPath]. */
    @Query("UPDATE vault_items SET localDocumentFilePath = :path WHERE id = :id")
    suspend fun updateLocalDocumentPath(id: String, path: String)
}
