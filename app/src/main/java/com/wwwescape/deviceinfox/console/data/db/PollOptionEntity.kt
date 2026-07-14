package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per option on a poll message — created once alongside the message and never edited
 * (options can't be added/changed/removed after sending, matching WhatsApp). */
@Entity(
    tableName = "poll_options",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class PollOptionEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val optionText: String,
    val orderIndex: Int,
    /** Comma-joined via [Converters.fromStringList]/[toStringList] — always small (at most 2
     * entries, this app's whole premise) and fully server-authoritative, re-synced wholesale on
     * every poll update rather than mutated locally, so there's no need for a separate normalized
     * vote table the way [ReactionEntity] needs one (a reaction is mutated directly by this
     * device; a vote is always applied by replacing the server's own resolved list). */
    val votedByPartnerIds: List<String> = emptyList(),
)
