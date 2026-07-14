package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Transaction
    @Query("SELECT * FROM calendar_events ORDER BY startAtEpochMillis ASC")
    fun observeAllWithAttachments(): Flow<List<CalendarEventWithAttachments>>

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    /** Read before every upsert that carries a cover photo — `CalendarRepository.resolveCoverPhoto`
     * needs the previously-stored `coverMediaId`/`coverFilePath` to tell "unchanged" from
     * "replaced" apart, since that decision has to happen before the new row overwrites it. */
    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: String): CalendarEventEntity?

    /** Every event row, no attachment join — used only to sweep every event's cover photo file
     * before a full wipe (`CalendarRepository.sweepAllFiles`); mirrors
     * `CalendarAttachmentDao.getAllSnapshot()`'s same role for the attachments table. */
    @Query("SELECT * FROM calendar_events")
    suspend fun getAllSnapshot(): List<CalendarEventEntity>

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()

    @Query("DELETE FROM calendar_events WHERE id NOT IN (:keepIds)")
    suspend fun deleteAllExcept(keepIds: List<String>)

    /** Full-sync reconcile — there's no incremental/paginated sync for planned dates (unlike
     * messaging's backward pagination), since a shared couple's calendar is expected to stay
     * small. Deliberately NOT a blind wipe-and-reinsert (`deleteAll()` then re-upsert-all, which
     * this used to do): `@Upsert` performs an in-place UPDATE for an existing primary key rather
     * than a delete+insert, so only removing events genuinely gone from the server (via
     * [deleteAllExcept]) — rather than deleting every row unconditionally — preserves FK-child
     * rows (calendar attachments) for events that still exist, instead of cascading them away
     * and forcing a full re-download on every sync. */
    @Transaction
    suspend fun replaceAll(events: List<CalendarEventEntity>) {
        // Room generates an invalid empty `NOT IN ()` clause for a zero-element list parameter —
        // deleteAll() is the correct (and only valid) way to express "keep nothing" anyway.
        if (events.isEmpty()) {
            deleteAll()
        } else {
            deleteAllExcept(events.map { it.id })
        }
        events.forEach { upsert(it) }
    }
}
