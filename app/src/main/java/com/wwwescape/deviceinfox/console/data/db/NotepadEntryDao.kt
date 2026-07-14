package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotepadEntryDao {
    @Query("SELECT * FROM notepad_entries WHERE type = :type ORDER BY updatedAtEpochMillis DESC")
    fun observeByType(type: NotepadEntryType): Flow<List<NotepadEntryEntity>>

    @Query("SELECT * FROM notepad_entries WHERE id = :id")
    suspend fun getById(id: String): NotepadEntryEntity?

    @Query("SELECT * FROM notepad_entries WHERE id = :id")
    fun observeById(id: String): Flow<NotepadEntryEntity?>

    /** Used by the refresh-then-prune sweep (see `NotepadRepository.refresh`) to find local rows
     * the server no longer has — same shape `VaultItemDao.getAllSnapshot()` already uses. */
    @Query("SELECT * FROM notepad_entries WHERE type = :type")
    suspend fun getAllSnapshotByType(type: NotepadEntryType): List<NotepadEntryEntity>

    @Upsert
    suspend fun upsert(entry: NotepadEntryEntity)

    @Query("DELETE FROM notepad_entries WHERE id = :id")
    suspend fun delete(id: String)
}
