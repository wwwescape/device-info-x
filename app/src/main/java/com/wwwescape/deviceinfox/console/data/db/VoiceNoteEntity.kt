package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId", unique = true)],
)
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val filePath: String,
    val durationMillis: Long,
)
