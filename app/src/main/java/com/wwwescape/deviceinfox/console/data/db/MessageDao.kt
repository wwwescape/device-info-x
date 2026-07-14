package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** Includes soft-deleted rows — they still render as tombstones, not removed from the list. */
    @Transaction
    @Query("SELECT * FROM messages ORDER BY sentAtEpochMillis ASC")
    fun observeConversation(): Flow<List<MessageWithDetails>>

    /** Backs the Starred messages screen. Excludes real (sender-initiated) deletes at the query
     * level — `isDeleted = 0` — but a "Delete for me" hide is a separate table
     * ([HiddenMessageDao]) this query can't see, so that half of tombstone-exclusion still
     * happens where it already does for the main conversation: `toConsoleMessage`'s `hiddenIds`
     * check, filtered post-mapping in `MessageRepository.starredMessages`. */
    @Transaction
    @Query("SELECT * FROM messages WHERE isStarred = 1 AND isDeleted = 0 ORDER BY sentAtEpochMillis ASC")
    fun observeStarred(): Flow<List<MessageWithDetails>>

    /** Backs the Pinned messages screen — same tombstone-exclusion split as [observeStarred]. */
    @Transaction
    @Query("SELECT * FROM messages WHERE isPinned = 1 AND isDeleted = 0 ORDER BY sentAtEpochMillis ASC")
    fun observePinned(): Flow<List<MessageWithDetails>>

    @Insert
    suspend fun insert(message: MessageEntity)

    /** Sync (Phase 11) writes through this instead of [insert] — re-fetching history/re-applying
     * a server response for a message that's already cached locally must not crash on a
     * primary-key conflict the way `@Insert`'s default ABORT strategy would. */
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET body = :body, editedAtEpochMillis = :editedAtEpochMillis WHERE id = :id")
    suspend fun editBody(id: String, body: String, editedAtEpochMillis: Long)

    /** Marks the optimistic local row a send created as failed once the server round-trip
     * errors out, so the composer can show a "not sent" indicator instead of the message just
     * vanishing or sitting there looking indistinguishable from a delivered one. */
    @Query("UPDATE messages SET deliveryState = :state WHERE id = :id")
    suspend fun setDeliveryState(id: String, state: MessageDeliveryState)

    /** Removes the optimistic local row (keyed by the client-generated id) once the real,
     * server-assigned row has been upserted in its place — see
     * [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.sendText]. */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    /** Local mirror of `message_service.wipe_conversation` — a genuine hard delete of every
     * message, not the tombstone [softDelete] leaves. Room's own `@ForeignKey(onDelete = CASCADE)`
     * on [ReactionEntity]/[AttachmentEntity]/[VoiceNoteEntity] cleans those rows automatically;
     * this only needs to touch the `messages` table itself. */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("UPDATE messages SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE messages SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE messages SET isRead = 1 WHERE senderId != :selfPartnerId AND isRead = 0")
    suspend fun markIncomingAsRead(selfPartnerId: String)

    /** Applied from the WebSocket's `message.read` event, which carries both a `message_id` and
     * the real `read_at` timestamp (`message_service.mark_read`) — unlike [markIncomingAsRead],
     * this doesn't check `senderId`, since the event is only ever sent for a message this device
     * actually sent. `deliveredAtEpochMillis` is backfilled to the same value only if it isn't
     * already set (`COALESCE`) — mirrors the server's own `mark_read`, which backfills
     * `delivered_at` server-side "in case the client's delivered ack never landed," a read
     * necessarily implying delivery even if this device never separately learned when delivery
     * itself happened. */
    @Query(
        "UPDATE messages SET isRead = 1, readAtEpochMillis = :readAtEpochMillis, " +
            "deliveredAtEpochMillis = COALESCE(deliveredAtEpochMillis, :readAtEpochMillis) WHERE id = :id",
    )
    suspend fun setRead(id: String, readAtEpochMillis: Long)

    /** Applied from the WebSocket's `message.delivered` event, same shape as [setRead] — carries
     * the real `delivered_at` timestamp (`message_service.mark_delivered`) rather than just the
     * bare enum transition [setDeliveryState] does for the (timestamp-less) optimistic-send-failed
     * case. */
    @Query("UPDATE messages SET deliveryState = 'DELIVERED', deliveredAtEpochMillis = :deliveredAtEpochMillis WHERE id = :id")
    suspend fun setDelivered(id: String, deliveredAtEpochMillis: Long)

    /** The local backward-pagination cursor — the oldest message Room already has, so
     * [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.loadOlderMessages] knows
     * where to ask the server to continue from. Null only for an empty conversation. */
    @Query("SELECT MIN(sentAtEpochMillis) FROM messages")
    suspend fun oldestMessageTimestamp(): Long?

    /** The local forward-sync watermark — the newest message Room already has, so
     * [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.syncConversation] can
     * ask the server only for what's newer (`after=`) instead of unconditionally re-walking
     * history it already has on every sync. Null only for an empty conversation (nothing synced
     * yet — a fresh install or a fresh pairing). */
    @Query("SELECT MAX(sentAtEpochMillis) FROM messages")
    suspend fun newestMessageTimestamp(): Long?

    /** Applied from the WebSocket's `message.link_preview_ready` event — a narrow, single-purpose
     * update (same shape as [setPinned]/[setStarred]/[setRead] above), not a full [upsert], since
     * that event only ever carries the 4 preview fields, not a whole message payload. */
    @Query(
        "UPDATE messages SET linkPreviewUrl = :url, linkPreviewTitle = :title, " +
            "linkPreviewDescription = :description, linkPreviewImagePath = :imagePath WHERE id = :id",
    )
    suspend fun setLinkPreview(id: String, url: String, title: String?, description: String?, imagePath: String?)

    /** Read before every [upsert] that might touch this row's preview fields (full resync,
     * `message.new`/`message.updated`) so an already-downloaded image isn't silently wiped back
     * to null and re-downloaded on every reconnect — see `MessageRepository.applyRemoteMessage`. */
    @Query("SELECT linkPreviewImagePath FROM messages WHERE id = :id")
    suspend fun linkPreviewImagePath(id: String): String?
}
