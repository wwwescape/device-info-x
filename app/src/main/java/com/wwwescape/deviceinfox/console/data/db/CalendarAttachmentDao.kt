package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CalendarAttachmentDao {
    @Insert
    suspend fun insert(attachment: CalendarAttachmentEntity)

    @Query("SELECT * FROM calendar_attachments WHERE eventId = :eventId")
    suspend fun getForEvent(eventId: String): List<CalendarAttachmentEntity>

    /** An event can have several attachments (unlike a message, which has at most one) — this is
     * how `CalendarRepository.downloadEventVideoIfNeeded`/`downloadEventDocumentIfNeeded` find
     * the specific row to update, since the `MessageAttachment` domain type carries no local db id. */
    @Query("SELECT * FROM calendar_attachments WHERE eventId = :eventId AND mediaId = :mediaId LIMIT 1")
    suspend fun getForEventAndMedia(eventId: String, mediaId: String): CalendarAttachmentEntity?

    @Query("DELETE FROM calendar_attachments WHERE id = :id")
    suspend fun delete(id: String)

    /** Used when an event is deleted outright — Room's FK cascade already removes the rows, this
     * exists only so the caller can snapshot file paths first (mirrors [AttachmentDao.getAllSnapshot]). */
    @Query("DELETE FROM calendar_attachments WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)

    @Query("UPDATE calendar_attachments SET localVideoFilePath = :path WHERE id = :id")
    suspend fun updateLocalVideoPath(id: String, path: String)

    @Query("UPDATE calendar_attachments SET localDocumentFilePath = :path WHERE id = :id")
    suspend fun updateLocalDocumentPath(id: String, path: String)

    /** One-shot snapshot (not a `Flow`) — used to recover every local file path before wiping
     * the calendar, since Room's FK cascade removes the DB rows but never the underlying files.
     * Mirrors [AttachmentDao.getAllSnapshot]. */
    @Query("SELECT * FROM calendar_attachments")
    suspend fun getAllSnapshot(): List<CalendarAttachmentEntity>
}
