package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PollOptionDao {
    @Insert
    suspend fun insertAll(options: List<PollOptionEntity>)

    /** Options themselves never change post-send, but vote state does — every poll update (a
     * fresh `MessageOutDto` from sync or a `message.poll_voted`/`message.poll_closed` WS event)
     * replaces the whole option set for that message rather than diffing individual rows, since
     * there are always few options (server caps at 12) and the server already sends the complete,
     * authoritative set every time. */
    @Query("DELETE FROM poll_options WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)
}
