package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAtEpochMillis ASC")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    /** Used by the refresh-then-prune sweep (see `ScheduledMessageRepository.refresh`) to find
     * local rows the server no longer has — same shape `NotepadEntryDao.getAllSnapshotByType`/
     * `VaultItemDao.getAllSnapshot()` already use. */
    @Query("SELECT * FROM scheduled_messages")
    suspend fun getAllSnapshot(): List<ScheduledMessageEntity>

    @Upsert
    suspend fun upsert(entry: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: String)
}
