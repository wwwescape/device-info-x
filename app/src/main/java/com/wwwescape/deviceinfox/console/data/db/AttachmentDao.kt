package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(attachment: AttachmentEntity)

    /** An attachment is immutable once a message is sent (only text messages can be edited) —
     * sync (Phase 11) checks this before downloading/inserting so re-syncing history doesn't
     * re-download media that's already cached locally. */
    @Query("SELECT EXISTS(SELECT 1 FROM attachments WHERE messageId = :messageId)")
    suspend fun existsForMessage(messageId: String): Boolean

    /** One-shot snapshot (not a `Flow`) — used to recover every [AttachmentEntity.filePath]
     * before wiping the conversation, since Room's FK cascade removes the DB rows but never the
     * underlying files. Mirrors [VaultItemDao.getAllSnapshot]'s same reasoning. */
    @Query("SELECT * FROM attachments")
    suspend fun getAllSnapshot(): List<AttachmentEntity>

    /** Called once a video's lazily-fetched bytes land locally — [AttachmentEntity.localVideoFilePath]
     * starts null for every received (non-sender) video row until this runs. */
    @Query("UPDATE attachments SET localVideoFilePath = :path WHERE id = :id")
    suspend fun updateLocalVideoPath(id: String, path: String)

    /** One attachment per message today (see [insert]'s call sites) — used by
     * `MessageRepository.downloadVideoIfNeeded`/`downloadDocumentIfNeeded` to find the row to
     * update, since the `MessageAttachment` domain type itself carries no local db id. */
    @Query("SELECT * FROM attachments WHERE messageId = :messageId LIMIT 1")
    suspend fun getForMessage(messageId: String): AttachmentEntity?

    /** Called once a document's lazily-fetched bytes land locally — mirrors [updateLocalVideoPath]. */
    @Query("UPDATE attachments SET localDocumentFilePath = :path WHERE id = :id")
    suspend fun updateLocalDocumentPath(id: String, path: String)
}
