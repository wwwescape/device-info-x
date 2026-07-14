package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entity: HiddenMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hideAll(entities: List<HiddenMessageEntity>)

    /** "Undo Delete" on a "Delete for me" tombstone. */
    @Query("DELETE FROM hidden_messages WHERE messageId = :messageId")
    suspend fun unhide(messageId: String)

    @Query("SELECT messageId FROM hidden_messages")
    fun observeAllHiddenIds(): Flow<List<String>>

    /** Called alongside [MessageDao.deleteAll] when wiping the conversation, so a full wipe
     * doesn't leave orphaned hidden-id rows pointing at now-gone messages. */
    @Query("DELETE FROM hidden_messages")
    suspend fun deleteAll()
}
