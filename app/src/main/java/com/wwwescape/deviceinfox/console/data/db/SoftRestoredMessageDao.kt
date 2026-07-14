package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoftRestoredMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entity: SoftRestoredMessageEntity)

    @Query("DELETE FROM soft_restored_messages WHERE messageId = :messageId")
    suspend fun remove(messageId: String)

    @Query("SELECT messageId FROM soft_restored_messages")
    fun observeAllIds(): Flow<List<String>>

    /** Same orphan-cleanup role as [HiddenMessageDao.deleteAll] on a full conversation wipe. */
    @Query("DELETE FROM soft_restored_messages")
    suspend fun deleteAll()
}
