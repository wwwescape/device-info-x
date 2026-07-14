package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReactionDao {
    @Upsert
    suspend fun upsert(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND partnerId = :partnerId")
    suspend fun delete(messageId: String, partnerId: String)

    /** Sync (Phase 11) clears a message's reactions before re-inserting the server's current
     * list, so a reaction removed server-side actually disappears locally instead of lingering. */
    @Query("DELETE FROM reactions WHERE messageId = :messageId")
    suspend fun deleteAllForMessage(messageId: String)
}
