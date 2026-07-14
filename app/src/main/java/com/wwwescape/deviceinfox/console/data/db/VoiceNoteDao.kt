package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface VoiceNoteDao {
    @Insert
    suspend fun insert(voiceNote: VoiceNoteEntity)

    /** See [AttachmentDao.existsForMessage] — same reasoning, same sync use. */
    @Query("SELECT EXISTS(SELECT 1 FROM voice_notes WHERE messageId = :messageId)")
    suspend fun existsForMessage(messageId: String): Boolean

    /** See [AttachmentDao.getAllSnapshot] — same reasoning, used when wiping the conversation. */
    @Query("SELECT * FROM voice_notes")
    suspend fun getAllSnapshot(): List<VoiceNoteEntity>

    /** See [AttachmentDao.getForMessage] — same one-per-message shape, used to free a single
     * deleted message's voice-note file without wiping the whole conversation. */
    @Query("SELECT * FROM voice_notes WHERE messageId = :messageId LIMIT 1")
    suspend fun getForMessage(messageId: String): VoiceNoteEntity?

    /** See [AttachmentDao.reattachToConfirmedMessage] — same reasoning, no `mediaId` column to
     * fill in here since a voice note's own file is never re-fetched (the sender already has it). */
    @Query("UPDATE voice_notes SET messageId = :newMessageId WHERE messageId = :oldMessageId")
    suspend fun reattachToConfirmedMessage(oldMessageId: String, newMessageId: String)
}
